package org.entur.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The app UIs over the real /mcp endpoint: hosts cache them by URI, so the URI a tool points
 * at has to change with the content and still resolve.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AppResourcesTest {

    private static final Map<String, AppResources.App> UI_TOOLS = Map.of(
        "trip", AppResources.App.TRIP,
        "departures", AppResources.App.DEPARTURES,
        "nearby-stops", AppResources.App.NEARBY_STOPS,
        "nearby-mobility", AppResources.App.NEARBY_MOBILITY);

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Each UI tool points at a versioned URI that reads back the app with its CSP")
    void toolResourceUris_shouldBeVersionedAndReadable() throws Exception {
        JsonNode tools = rpc("tools/list", Map.of()).get("tools");
        List<String> checked = new ArrayList<>();
        for (JsonNode tool : tools) {
            AppResources.App app = UI_TOOLS.get(tool.get("name").asText());
            if (app == null) {
                continue;
            }
            String uri = tool.get("_meta").get("ui").get("resourceUri").asText();
            assertThat(uri).isEqualTo(app.uri()).matches("ui://[a-z]+/[0-9a-f]{12}/[a-z-]+\\.html");

            JsonNode content = rpc("resources/read", Map.of("uri", uri)).get("contents").get(0);
            assertThat(content.get("uri").asText()).isEqualTo(uri);
            assertThat(content.get("mimeType").asText()).isEqualTo(AppResources.MIME_TYPE);
            assertThat(content.get("text").asText()).contains("<html").doesNotContain(AppHtmlLoader.TOKEN_MARKER);
            assertThat(content.get("_meta").get("ui").has("csp")).isTrue();
            checked.add(tool.get("name").asText());
        }
        assertThat(checked).containsExactlyInAnyOrderElementsOf(UI_TOOLS.keySet());
    }

    @Test
    @DisplayName("resources/list offers the versioned URIs, with their CSP")
    void resourcesList_shouldOfferVersionedUris() throws Exception {
        List<String> uris = new ArrayList<>();
        for (JsonNode resource : rpc("resources/list", Map.of()).get("resources")) {
            uris.add(resource.get("uri").asText());
            assertThat(resource.get("_meta").get("ui").has("csp")).isTrue();
        }
        assertThat(uris).containsExactlyInAnyOrderElementsOf(
            UI_TOOLS.values().stream().map(AppResources.App::uri).toList());
    }

    @Test
    @DisplayName("A URI from an earlier deploy still reads, so older conversations keep their UI")
    void olderVersion_shouldStillRead() throws Exception {
        String old = "ui://trip/000000000000/trip-map.html";

        JsonNode content = rpc("resources/read", Map.of("uri", old)).get("contents").get(0);

        assertThat(content.get("uri").asText()).isEqualTo(old);
        assertThat(content.get("text").asText()).contains("<html");
        assertThat(content.get("_meta").get("ui").has("csp")).isTrue();
    }

    private JsonNode rpc(String method, Map<String, Object> params) throws Exception {
        String body = mapper.writeValueAsString(
            Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode json = mapper.readTree(response.body().replaceFirst("^data:", ""));
        assertThat(json.has("error")).as("%s: %s", method, json.get("error")).isFalse();
        return json.get("result");
    }
}
