package org.entur.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UiPayload Unit Tests")
class UiPayloadTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Builds {"trip":{"tripPatterns":[{"legs":[{...},{...}]},{"legs":[{...}]}]}} */
    private Map<String, Object> tripFixture() {
        Map<String, Object> leg1 = new HashMap<>();
        leg1.put("mode", "rail");
        leg1.put("pointsOnLink", Map.of("points", "abc"));
        leg1.put("line", new HashMap<>(Map.of("publicCode", "R11",
            "presentation", Map.of("colour", "FF0000"))));

        Map<String, Object> leg2 = new HashMap<>();
        leg2.put("mode", "foot");
        // deliberately no pointsOnLink and no line -> exercises the null path

        Map<String, Object> leg3 = new HashMap<>();
        leg3.put("mode", "bus");
        leg3.put("pointsOnLink", Map.of("points", "xyz"));

        List<Object> legsA = new ArrayList<>(List.of(leg1, leg2));
        List<Object> legsB = new ArrayList<>(List.of(leg3));

        Map<String, Object> patternA = new HashMap<>(Map.of("legs", legsA));
        Map<String, Object> patternB = new HashMap<>(Map.of("legs", legsB));

        Map<String, Object> trip = new HashMap<>(Map.of(
            "tripPatterns", new ArrayList<>(List.of(patternA, patternB))));
        Map<String, Object> root = new HashMap<>();
        root.put("trip", trip);
        return root;
    }

    private String textOf(CallToolResult r) {
        return ((TextContent) r.content().get(0)).text();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> uiOf(CallToolResult r) {
        return (Map<String, Object>) r.meta().get(UiPayload.META_KEY);
    }

    @Test
    @DisplayName("Should remove nested list-valued paths from content")
    void split_removesNestedPathsFromContent() throws Exception {
        CallToolResult result = UiPayload.split(tripFixture(), mapper,
            "trip.tripPatterns[].legs[].pointsOnLink");

        assertThat(textOf(result)).doesNotContain("pointsOnLink");
        assertThat(textOf(result)).contains("\"mode\":\"rail\"");
    }

    @Test
    @DisplayName("Should nest one array level per [] segment, preserving index alignment")
    void split_preservesIndexAlignment() throws Exception {
        CallToolResult result = UiPayload.split(tripFixture(), mapper,
            "trip.tripPatterns[].legs[].pointsOnLink");

        List<List<Object>> values =
            (List<List<Object>>) uiOf(result).get("trip.tripPatterns[].legs[].pointsOnLink");

        assertThat(values).hasSize(2);           // two patterns
        assertThat(values.get(0)).hasSize(2);    // pattern 0 has two legs
        assertThat(values.get(1)).hasSize(1);    // pattern 1 has one leg
        assertThat(values.get(0).get(0)).isEqualTo(Map.of("points", "abc"));
        assertThat(values.get(0).get(1)).isNull();   // foot leg had none
        assertThat(values.get(1).get(0)).isEqualTo(Map.of("points", "xyz"));
    }

    @Test
    @DisplayName("Should not add an array level for non-list segments")
    void split_nonListSegmentAddsNoArrayLevel() throws Exception {
        CallToolResult result = UiPayload.split(tripFixture(), mapper,
            "trip.tripPatterns[].legs[].line.presentation");

        List<List<Object>> values =
            (List<List<Object>>) uiOf(result).get("trip.tripPatterns[].legs[].line.presentation");

        assertThat(values).hasSize(2);           // two patterns
        assertThat(values.get(0)).hasSize(2);    // pattern 0 has two legs
        assertThat(values.get(0).get(0)).isEqualTo(Map.of("colour", "FF0000"));
        assertThat(values.get(0).get(1)).isNull();   // leg2 has no line at all
        assertThat(values.get(1)).hasSize(1);    // pattern 1 has one leg
        assertThat(values.get(1).get(0)).isNull();   // leg3 has no presentation (no line field)
        assertThat(textOf(result)).doesNotContain("presentation");
        assertThat(textOf(result)).contains("R11");  // sibling field survives
    }

    @Test
    @DisplayName("Should handle multiple paths in one call")
    void split_handlesMultiplePaths() throws Exception {
        CallToolResult result = UiPayload.split(tripFixture(), mapper,
            "trip.tripPatterns[].legs[].pointsOnLink",
            "trip.tripPatterns[].legs[].line.presentation");

        assertThat(uiOf(result)).containsOnlyKeys(
            "trip.tripPatterns[].legs[].pointsOnLink",
            "trip.tripPatterns[].legs[].line.presentation");
        assertThat(textOf(result)).doesNotContain("pointsOnLink").doesNotContain("presentation");
    }

    @Test
    @DisplayName("Should tolerate paths that match nothing")
    void split_toleratesMissingPaths() throws Exception {
        Map<String, Object> root = new HashMap<>();
        root.put("trip", new HashMap<>());

        CallToolResult result = UiPayload.split(root, mapper,
            "trip.tripPatterns[].legs[].pointsOnLink",
            "nothing.here[].at.all");

        assertThat(textOf(result)).isEqualTo("{\"trip\":{}}");
        assertThat(uiOf(result)).isEmpty();
    }

    @Test
    @DisplayName("Should support a top-level list path")
    void split_topLevelListPath() throws Exception {
        Map<String, Object> v1 = new HashMap<>(Map.of("id", "a", "rentalUris", Map.of("web", "u1")));
        Map<String, Object> v2 = new HashMap<>(Map.of("id", "b"));
        Map<String, Object> root = new HashMap<>();
        root.put("vehicles", new ArrayList<>(List.of(v1, v2)));

        CallToolResult result = UiPayload.split(root, mapper, "vehicles[].rentalUris");

        List<Object> values = (List<Object>) uiOf(result).get("vehicles[].rentalUris");
        assertThat(values).containsExactly(Map.of("web", "u1"), null);
        assertThat(textOf(result)).doesNotContain("rentalUris");
    }

    @Test
    @DisplayName("text() should produce a result with no _meta")
    void text_producesPlainResult() {
        CallToolResult result = UiPayload.text("{\"error\":\"nope\"}");
        assertThat(textOf(result)).isEqualTo("{\"error\":\"nope\"}");
        assertThat(result.meta()).isNull();
    }
}
