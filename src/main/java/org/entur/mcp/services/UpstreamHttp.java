package org.entur.mcp.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Timeouts and size limits shared by every call to an upstream Entur API.
 *
 * <p>{@link HttpClient} waits forever by default, both to connect and for a response. Each tool
 * call holds a servlet thread while it waits, so a slow or stalled upstream - or a caller sending
 * deliberately expensive queries - would park threads until the pool is exhausted and the server
 * stops answering, health checks included. A timeout surfaces as {@link
 * java.net.http.HttpTimeoutException}, an {@code IOException}, which the services already turn
 * into their ordinary "network error" responses.
 *
 * <p>Likewise {@link HttpResponse.BodyHandlers#ofString()} reads a body of any size into memory.
 * Responses are read with {@link #boundedString()} instead, which gives up once a body passes
 * {@link #MAX_RESPONSE_BYTES}: before reading anything when the declared Content-Length is
 * already over, and while reading otherwise. That also surfaces as an {@code IOException}.
 */
final class UpstreamHttp {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    /** Generous enough for a long OTP trip search; anything slower is treated as down. */
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    /**
     * Roughly ten times the largest response seen: every situation in the country (unfiltered
     * {@code alerts}) is about 0.5 MB, a 50-pattern Oslo-Bergen trip about 0.15 MB.
     */
    static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024;

    private UpstreamHttp() {}

    static HttpClient newClient() {
        return HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    }

    /** A UTF-8 string body, refused once it passes {@link #MAX_RESPONSE_BYTES}. */
    static HttpResponse.BodyHandler<String> boundedString() {
        return boundedString(MAX_RESPONSE_BYTES);
    }

    static HttpResponse.BodyHandler<String> boundedString(int maxBytes) {
        return info -> new BoundedStringSubscriber(maxBytes,
            info.headers().firstValueAsLong("Content-Length").orElse(-1));
    }

    /** Thrown when an upstream body is larger than allowed. */
    static final class ResponseTooLargeException extends IOException {
        ResponseTooLargeException(int maxBytes) {
            super("Upstream response exceeds " + maxBytes + " bytes");
        }
    }

    private static final class BoundedStringSubscriber implements HttpResponse.BodySubscriber<String> {

        private final int maxBytes;
        private final long declaredLength;
        private final CompletableFuture<String> result = new CompletableFuture<>();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        BoundedStringSubscriber(int maxBytes, long declaredLength) {
            this.maxBytes = maxBytes;
            this.declaredLength = declaredLength;
        }

        @Override
        public CompletionStage<String> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (declaredLength > maxBytes) {
                tooLarge();
                return;
            }
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) {
                return;
            }
            for (ByteBuffer buffer : buffers) {
                int n = buffer.remaining();
                if (body.size() + (long) n > maxBytes) {
                    tooLarge();
                    return;
                }
                byte[] bytes = new byte[n];
                buffer.get(bytes);
                body.write(bytes, 0, n);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            // JSON is UTF-8 (RFC 8259), which every Entur API sends
            result.complete(body.toString(StandardCharsets.UTF_8));
        }

        private void tooLarge() {
            subscription.cancel();
            result.completeExceptionally(new ResponseTooLargeException(maxBytes));
        }
    }
}
