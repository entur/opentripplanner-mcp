package org.entur.mcp.services;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Timeouts shared by every call to an upstream Entur API.
 *
 * <p>{@link HttpClient} waits forever by default, both to connect and for a response. Each tool
 * call holds a servlet thread while it waits, so a slow or stalled upstream - or a caller sending
 * deliberately expensive queries - would park threads until the pool is exhausted and the server
 * stops answering, health checks included. A timeout surfaces as {@link
 * java.net.http.HttpTimeoutException}, an {@code IOException}, which the services already turn
 * into their ordinary "network error" responses.
 */
final class UpstreamHttp {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    /** Generous enough for a long OTP trip search; anything slower is treated as down. */
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private UpstreamHttp() {}

    static HttpClient newClient() {
        return HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    }
}
