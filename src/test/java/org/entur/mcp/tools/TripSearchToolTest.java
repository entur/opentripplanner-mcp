package org.entur.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@DisplayName("TripSearchTool Integration Tests")
class TripSearchToolTest {

    @Autowired
    TripSearchTool tripSearchTool;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should successfully plan trip between Oslo and Asker")
    void trip_endToEnd_shouldReturnValidJSON() {
        // Act
        String result = tripSearchTool.trip("Oslo S", "Asker", null, null, 3);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).doesNotContain("\"error\"");

        // Verify it's valid JSON
        assertThatCode(() -> {
            Map<String, Object> json = objectMapper.readValue(result, Map.class);
            assertThat(json).containsKey("trip");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should return error for invalid location")
    void trip_withInvalidLocation_shouldReturnError() {
        // Act
        String result = tripSearchTool.trip("", "Asker", null, null, 3);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).contains("\"error\"");
        assertThat(result).contains("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("Should successfully geocode Asker Stasjon")
    void geocode_endToEnd_shouldReturnFeatures() {
        // Act
        String result = tripSearchTool.geocode("Asker Stasjon", 3);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).doesNotContain("\"error\"");

        // Verify it's valid JSON with features
        assertThatCode(() -> {
            Map<String, Object> json = objectMapper.readValue(result, Map.class);
            assertThat(json).containsKey("features");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should return error for empty geocode text")
    void geocode_withEmptyText_shouldReturnError() {
        // Act
        String result = tripSearchTool.geocode("", 3);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).contains("\"error\"");
        assertThat(result).contains("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("Should plan trip with departure time")
    void trip_withDepartureTime_shouldSucceed() {
        // Act
        String result = tripSearchTool.trip("Oslo S", "Asker", "2025-12-25T10:00:00", null, 3);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).doesNotContain("\"error\"");
    }

    @Test
    @DisplayName("Should return error when both departure and arrival times are set")
    void trip_withBothTimes_shouldReturnError() {
        // Act
        String result = tripSearchTool.trip("Oslo S", "Asker",
            "2025-12-25T10:00:00", "2025-12-25T14:00:00", 3);

        // Assert
        assertThat(result).contains("\"error\"");
        assertThat(result).contains("VALIDATION_ERROR");
        assertThat(result).contains("Cannot specify both");
    }
}