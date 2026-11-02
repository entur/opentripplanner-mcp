package org.entur.mcp.services;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Summarizes GraphQL {@code errors} for logs above DEBUG.
 *
 * <p>A GraphQL error message can quote the variables it rejected ("Variable 'from' has an invalid
 * value ..."), which are the user's places and coordinates. The summary keeps only the count and
 * each error's classification or code; the full errors are for DEBUG.
 */
final class GraphQlErrors {

    private GraphQlErrors() {
    }

    static String summary(List<?> errors) {
        List<String> kinds = errors.stream()
            .map(GraphQlErrors::kind)
            .distinct()
            .toList();
        return errors.size() + " error(s), kinds=" + kinds;
    }

    private static String kind(Object error) {
        if (error instanceof Map<?, ?> e && e.get("extensions") instanceof Map<?, ?> ext) {
            Object kind = ext.get("classification") != null ? ext.get("classification") : ext.get("code");
            if (kind != null) {
                return Objects.toString(kind);
            }
        }
        return "unclassified";
    }
}
