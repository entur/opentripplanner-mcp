package org.entur.mcp.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestBodySizeLimitFilterTest {

    private static final String INITIALIZE = """
        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
         "capabilities":{},"clientInfo":{"name":"test","version":"1"},"padding":"%s"}}""";

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void normalMcpRequestIsAccepted() throws Exception {
        assertThat(post(INITIALIZE.formatted(""), false).statusCode()).isEqualTo(200);
    }

    @Test
    void declaredOversizedBodyIsRefusedWith413() throws Exception {
        String body = INITIALIZE.formatted("x".repeat(RequestBodySizeLimitFilter.MAX_BODY_BYTES));
        assertThat(post(body, false).statusCode()).isEqualTo(413);
    }

    @Test
    void chunkedOversizedBodyIsNotProcessed() throws Exception {
        String body = INITIALIZE.formatted("x".repeat(RequestBodySizeLimitFilter.MAX_BODY_BYTES));
        assertThat(post(body, true).statusCode()).isGreaterThanOrEqualTo(400);
    }

    private HttpResponse<String> post(String body, boolean chunked) throws Exception {
        byte[] bytes = body.getBytes();
        HttpRequest.BodyPublisher publisher = chunked
            // An InputStream publisher has no known length, so the client sends it chunked
            ? HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes))
            : HttpRequest.BodyPublishers.ofByteArray(bytes);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(publisher)
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
