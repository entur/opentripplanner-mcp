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
 * Every tool this server registers only reads public data. This holds that claim over the real
 * tools/list: a new tool fails here until it is added below, which is the moment to decide
 * whether it is read-only too.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ToolAnnotationsTest {

    private static final List<String> READ_ONLY_TOOLS = List.of(
        "trip", "geocode", "departures", "alerts", "nearby-stops", "nearby-mobility",
        "poll-departures", "poll-trip");

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("The registered tools are exactly the known ones, each marked read-only")
    void everyTool_shouldBeKnownAndReadOnly() throws Exception {
        List<String> names = new ArrayList<>();
        for (JsonNode tool : rpc("tools/list").get("tools")) {
            String name = tool.get("name").asText();
            names.add(name);
            JsonNode annotations = tool.get("annotations");
            assertThat(annotations).as("%s has annotations", name).isNotNull();
            assertThat(annotations.get("readOnlyHint").asBoolean()).as("%s readOnlyHint", name).isTrue();
            assertThat(annotations.get("destructiveHint").asBoolean()).as("%s destructiveHint", name).isFalse();
        }
        assertThat(names).containsExactlyInAnyOrderElementsOf(READ_ONLY_TOOLS);
    }

    private JsonNode rpc(String method) throws Exception {
        String body = mapper.writeValueAsString(
            Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", Map.of()));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return mapper.readTree(response.body().replaceFirst("^data:", "")).get("result");
    }
}
