package org.entur.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits a tool payload into model-facing JSON and UI-only data.
 *
 * <p>The model-facing JSON becomes {@code content[0].text}; the extracted fields go to
 * {@code _meta["org.entur/ui"]}, keyed by the path they were extracted from so each UI app
 * can graft them back by index.
 *
 * <p>Path syntax: dot-separated segments, where a {@code []} suffix means "iterate this list".
 * Extracted values gain one array level per {@code []} segment; plain segments add none.
 * {@code trip.tripPatterns[].legs[].pointsOnLink} therefore yields a list-of-lists indexed
 * [pattern][leg].
 *
 * <p>Note: {@link #split} mutates the supplied map in place, and — because the mutation is a
 * {@code remove} applied via recursive traversal — everything reachable from it: nested maps
 * and lists are stripped in place too, not just the top-level map. Callers pass a map built
 * fresh per request from the JSON response, so this is safe today. But a shallow
 * {@code new HashMap<>(response)} copy does NOT protect the nested structure — if a service
 * ever starts caching and reusing response graphs (e.g. to avoid re-fetching upstream data),
 * the second call would receive an already-stripped payload with no error or warning. Do not
 * pass a map — or any map built from a shared/cached response graph — into this method.
 */
public final class UiPayload {

    /** Reverse-namespaced per MCP convention, to avoid colliding with host-reserved _meta keys. */
    public static final String META_KEY = "org.entur/ui";

    private UiPayload() {}

    public static CallToolResult split(Map<String, Object> full, ObjectMapper mapper, String... paths)
            throws JsonProcessingException {
        Map<String, Object> ui = new LinkedHashMap<>();
        for (String path : paths) {
            Object extracted = extract(full, path.split("\\."), 0);
            if (extracted != null && hasValue(extracted)) {
                ui.put(path, extracted);
            }
        }
        return CallToolResult.builder()
            .addTextContent(mapper.writeValueAsString(full))
            .meta(Map.of(META_KEY, ui))
            .build();
    }

    /** Wraps already-serialised JSON with no _meta. Used for error responses and un-split tools. */
    public static CallToolResult text(String json) {
        return CallToolResult.builder().addTextContent(json).build();
    }

    /**
     * Checks if an extracted structure contains any non-null values.
     * A List has a value if any element does; any other non-null object is a value.
     */
    private static boolean hasValue(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof List<?> list) {
            for (Object item : list) {
                if (hasValue(item)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Object extract(Object node, String[] segments, int index) {
        if (!(node instanceof Map)) {
            return null;
        }
        Map<String, Object> map = (Map<String, Object>) node;
        String segment = segments[index];

        if (index == segments.length - 1) {
            return map.remove(segment);
        }

        if (segment.endsWith("[]")) {
            Object listValue = map.get(segment.substring(0, segment.length() - 2));
            if (!(listValue instanceof List<?> list)) {
                return null;
            }
            List<Object> extracted = new ArrayList<>(list.size());
            for (Object item : list) {
                extracted.add(extract(item, segments, index + 1));
            }
            return extracted;
        }

        return extract(map.get(segment), segments, index + 1);
    }
}
