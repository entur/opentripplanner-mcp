package org.entur.mcp.services;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UpstreamHttp bounded bodies")
class UpstreamHttpTest {

    private static final int LIMIT = 1024;

    private MockWebServer server;
    private final HttpClient client = UpstreamHttp.newClient();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private String get() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(server.url("/").uri()).GET().build();
        return client.send(request, UpstreamHttp.boundedString(LIMIT)).body();
    }

    @Test
    @DisplayName("A body within the limit is read as UTF-8")
    void withinLimit_shouldBeRead() throws Exception {
        String body = "{\"name\":\"Støren\"}" + "x".repeat(LIMIT - 20);
        server.enqueue(new MockResponse.Builder().body(body).build());

        assertThat(get()).isEqualTo(body);
    }

    @Test
    @DisplayName("A declared Content-Length over the limit is refused")
    void declaredLengthOverLimit_shouldBeRefused() {
        server.enqueue(new MockResponse.Builder().body("x".repeat(LIMIT + 1)).build());

        assertThatThrownBy(this::get)
            .isInstanceOf(IOException.class)
            .hasMessageContaining("exceeds " + LIMIT + " bytes");
    }

    @Test
    @DisplayName("A chunked body is cut off once it passes the limit")
    void chunkedBodyOverLimit_shouldBeRefused() {
        server.enqueue(new MockResponse.Builder().chunkedBody("x".repeat(LIMIT * 4), 100).build());

        assertThatThrownBy(this::get)
            .isInstanceOf(IOException.class)
            .hasMessageContaining("exceeds " + LIMIT + " bytes");
    }
}
