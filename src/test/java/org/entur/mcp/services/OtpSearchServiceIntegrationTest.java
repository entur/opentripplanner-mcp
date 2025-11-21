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

    // ==================== Departure Board Integration Tests ====================

    @Test
    @DisplayName("handleDepartureBoardRequest should return departures for valid stop")
    public void handleDepartureBoardRequest_withValidStop_shouldReturnDepartures() throws Exception {
        // Act
        Map<String, Object> result = otpSearchService.handleDepartureBoardRequest(
                "NSR:StopPlace:337", // Oslo S
                5,
                null,
                60,
                null
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsKey("stopPlace");

        @SuppressWarnings("unchecked")
        Map<String, Object> stopPlace = (Map<String, Object>) result.get("stopPlace");
        assertThat(stopPlace).containsKey("estimatedCalls");
        assertThat(stopPlace).containsKey("id");
        assertThat(stopPlace).containsKey("name");
    }

    @Test
    @DisplayName("handleDepartureBoardRequest should return departures with mode filter")
    public void handleDepartureBoardRequest_withModeFilter_shouldReturnFilteredDepartures() throws Exception {
        // Act
        Map<String, Object> result = otpSearchService.handleDepartureBoardRequest(
                "NSR:StopPlace:337", // Oslo S
                10,
                null,
                120,
                java.util.List.of("rail")
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsKey("stopPlace");
    }

    @Test
    @DisplayName("handleDepartureBoardRequest should respect numberOfDepartures limit")
    public void handleDepartureBoardRequest_shouldRespectNumberOfDepartures() throws Exception {
        // Act
        Map<String, Object> result = otpSearchService.handleDepartureBoardRequest(
                "NSR:StopPlace:337",
                2,
                null,
                120,
                null
        );

        // Assert
        assertThat(result).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> stopPlace = (Map<String, Object>) result.get("stopPlace");
        @SuppressWarnings("unchecked")
        java.util.List<Object> estimatedCalls = (java.util.List<Object>) stopPlace.get("estimatedCalls");

        // Should have at most 2 departures
        assertThat(estimatedCalls).hasSizeLessThanOrEqualTo(2);
    }
}