package org.entur.mcp.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("OtpSearchService Integration Tests")
class OtpSearchServiceIntegrationTest {

    @Autowired
    OtpSearchService otpSearchService;

    @Test
    @DisplayName("OtpSearchService bean should be autowired")
    public void otpSearchService_shouldBeAutowired() {
        assertThat(otpSearchService).isNotNull();
    }

    @Test
    @DisplayName("Should plan trip between Oslo S and Asker")
    public void handleTripRequest_withRealLocations_shouldReturnTripPatterns() throws Exception {
        // Act
        Map<String, Object> result = otpSearchService.handleTripRequest(
                "Oslo S",
                "Asker",
                null,
                null,
                3
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsKey("trip");

        @SuppressWarnings("unchecked")
        Map<String, Object> trip = (Map<String, Object>) result.get("trip");
        assertThat(trip).containsKey("tripPatterns");
    }

    @Test
    @DisplayName("Should plan trip with departure time")
    public void handleTripRequest_withDepartureTime_shouldIncludeInRequest() throws Exception {
        // Arrange
        String departureTime = "2025-12-25T10:00:00";

        // Act
        Map<String, Object> result = otpSearchService.handleTripRequest(
                "Oslo S",
                "Asker",
                departureTime,
                null,
                3
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsKey("trip");
    }

    @Test
    @DisplayName("Should plan trip with coordinates")
    public void handleTripRequest_withCoordinates_shouldWork() throws Exception {
        // Act
        Map<String, Object> result = otpSearchService.handleTripRequest(
                "59.911,10.748",  // Oslo S coordinates
                "59.832,10.433",   // Asker coordinates
                null,
                null,
                3
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsKey("trip");
    }

    @Test
    @DisplayName("Should limit trip patterns to maxResults")
    public void handleTripRequest_shouldRespectMaxResults() throws Exception {
        // Act
        Map<String, Object> result = otpSearchService.handleTripRequest(
                "Oslo S",
                "Asker",
                null,
                null,
                1
        );

        // Assert
        assertThat(result).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> trip = (Map<String, Object>) result.get("trip");
        @SuppressWarnings("unchecked")
        java.util.List<Object> tripPatterns = (java.util.List<Object>) trip.get("tripPatterns");

        assertThat(tripPatterns).hasSizeLessThanOrEqualTo(1);
    }
}