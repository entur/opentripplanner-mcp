package org.entur.mcp.tools;

import java.util.List;

/** Reads tool results for the metrics' ok / no_results outcome. */
final class ToolResults {

    private ToolResults() {
    }

    /** Whether {@code value} is a non-empty list; a missing or non-list value has no items. */
    static boolean hasItems(Object value) {
        return value instanceof List<?> list && !list.isEmpty();
    }
}
