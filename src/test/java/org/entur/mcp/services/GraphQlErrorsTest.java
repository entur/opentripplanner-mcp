package org.entur.mcp.services;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GraphQlErrorsTest {

    @Test
    void summaryKeepsKindsButNotMessages() {
        List<?> errors = List.of(
            Map.of("message", "Variable 'from' has an invalid value: Storgata 1",
                   "extensions", Map.of("classification", "ValidationError")),
            Map.of("message", "boom", "extensions", Map.of("code", "INTERNAL")),
            Map.of("message", "no extensions"));

        String summary = GraphQlErrors.summary(errors);

        assertThat(summary)
            .isEqualTo("3 error(s), kinds=[ValidationError, INTERNAL, unclassified]")
            .doesNotContain("Storgata");
    }
}
