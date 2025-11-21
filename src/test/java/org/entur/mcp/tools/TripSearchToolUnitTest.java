package org.entur.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.entur.mcp.exception.GeocodingException;
import org.entur.mcp.exception.TripPlanningException;
import org.entur.mcp.exception.ValidationException;
import org.entur.mcp.services.GeocoderService;
import org.entur.mcp.services.OtpSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TripSearchTool Unit Tests")
class TripSearchToolUnitTest {

    @Mock
    private OtpSearchService otpSearchService;

    @Mock
    private GeocoderService geocoderService;

    private TripSearchTool tripSearchTool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        tripSearchTool = new TripSearchTool(otpSearchService, geocoderService);
        objectMapper = new ObjectMapper();
    }

    // ==================== Trip Tool Success Tests ====================

    @Test
    @DisplayName("Trip tool should return valid JSON on success")
    void trip_withValidInputs_shouldReturnJSON() throws Exception {
        // Arrange
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("trip", Map.of("tripPatterns", List.of()));

        when(otpSearchService.handleTripRequest(anyString(), anyString(), any(), any(), any()))
            .thenReturn(mockResponse);

        // Act
        String result = tripSearchTool.trip("Oslo S", "Asker", null, null, 3);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).contains("trip");
        assertThat(result).doesNotContain("error");

        // Verify it's valid JSON
        assertThatCode(() -> objectMapper.readTree(result))
            .doesNotThrowAnyException();
    }

    // ==================== Trip Tool Error Handling Tests ====================

    @Test
    @DisplayName("Trip tool should return error response on ValidationException")
    void trip_withValidationException_shouldReturnErrorResponse() throws Exception {
        // Arrange
        when(otpSearchService.handleTripRequest(anyString(), anyString(), any(), any(), any()))
            .thenThrow(new ValidationException("from", "from cannot be empty"));

        // Act
        String result = tripSearchTool.trip("", "Asker", null, null, 3);

        // Assert
        assertThat(result).contains("VALIDATION_ERROR");
        assertThat(result).contains("from");
        assertThat(result).contains("from cannot be empty");

        Map<String, Object> errorMap = objectMapper.readValue(result, Map.class);
        assertThat(errorMap).containsEntry("error", "VALIDATION_ERROR");
        assertThat(errorMap).containsEntry("type", "ValidationException");
    }

    @Test
    @DisplayName("Trip tool should return error response on GeocodingException")
    void trip_withGeocodingException_shouldReturnErrorResponse() throws Exception {
        // Arrange
        when(otpSearchService.handleTripRequest(anyString(), anyString(), any(), any(), any()))
            .thenThrow(new GeocodingException("Unknown Place", "Location not found"));

        // Act
        String result = tripSearchTool.trip("Unknown Place", "Asker", null, null, 3);

        // Assert
        assertThat(result).contains("GEOCODING_ERROR");
        assertThat(result).contains("Unknown Place");
        assertThat(result).contains("Location not found");

        Map<String, Object> errorMap = objectMapper.readValue(result, Map.class);
        assertThat(errorMap).containsEntry("error", "GEOCODING_ERROR");
        assertThat(errorMap).containsEntry("type", "GeocodingException");
    }

    @Test
    @DisplayName("Trip tool should return error response on TripPlanningException")
    void trip_withTripPlanningException_shouldReturnErrorResponse() throws Exception {
        // Arrange
        when(otpSearchService.handleTripRequest(anyString(), anyString(), any(), any(), any()))
            .thenThrow(new TripPlanningException("API unavailable"));

        // Act
        String result = tripSearchTool.trip("Oslo", "Asker", null, null, 3);

        // Assert
        assertThat(result).contains("TRIP_PLANNING_ERROR");
        assertThat(result).contains("API unavailable");

        Map<String, Object> errorMap = objectMapper.readValue(result, Map.class);
        assertThat(errorMap).containsEntry("error", "TRIP_PLANNING_ERROR");
        assertThat(errorMap).containsEntry("type", "TripPlanningException");
    }

    @Test
    @DisplayName("Trip tool should return generic error on unexpected exception")
    void trip_withUnexpectedException_shouldReturnGenericError() throws Exception {
        // Arrange
        when(otpSearchService.handleTripRequest(anyString(), anyString(), any(), any(), any()))
            .thenThrow(new RuntimeException("Unexpected error"));

        // Act
        String result = tripSearchTool.trip("Oslo", "Asker", null, null, 3);

        // Assert
        assertThat(result).contains("ERROR");
        assertThat(result).contains("An unexpected error occurred");
        assertThat(result).contains("Unexpected error");

        Map<String, Object> errorMap = objectMapper.readValue(result, Map.class);
        assertThat(errorMap).containsEntry("error", "ERROR");
        assertThat(errorMap).containsEntry("type", "Exception");
    }

    // ==================== Geocode Tool Success Tests ====================

    @Test
    @DisplayName("Geocode tool should return valid JSON on success")
    void geocode_withValidInput_shouldReturnJSON() throws Exception {
        // Arrange
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("features", List.of(
            Map.of("properties", Map.of("name", "Oslo S"))
        ));

        when(geocoderService.handleGeocodeRequest(anyString(), anyInt()))
            .thenReturn(mockResponse);

        // Act
        String result = tripSearchTool.geocode("Oslo S", 5);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).contains("features");
        assertThat(result).doesNotContain("error");

        // Verify it's valid JSON
        assertThatCode(() -> objectMapper.readTree(result))
            .doesNotThrowAnyException();
    }

    // ==================== Geocode Tool Error Handling Tests ====================

    @Test
    @DisplayName("Geocode tool should return error response on ValidationException")
    void geocode_withValidationException_shouldReturnErrorResponse() throws Exception {
        // Arrange
        when(geocoderService.handleGeocodeRequest(anyString(), anyInt()))
            .thenThrow(new ValidationException("text", "text cannot be empty"));

        // Act
        String result = tripSearchTool.geocode("", 5);

        // Assert
        assertThat(result).contains("VALIDATION_ERROR");
        assertThat(result).contains("text");

        Map<String, Object> errorMap = objectMapper.readValue(result, Map.class);
        assertThat(errorMap).containsEntry("error", "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("Geocode tool should return error response on GeocodingException")
    void geocode_withGeocodingException_shouldReturnErrorResponse() throws Exception {
        // Arrange
        when(geocoderService.handleGeocodeRequest(anyString(), anyInt()))
            .thenThrow(new GeocodingException("test", "Geocoding failed"));

        // Act
        String result = tripSearchTool.geocode("test", 5);

        // Assert
        assertThat(result).contains("GEOCODING_ERROR");
        assertThat(result).contains("Geocoding failed");

        Map<String, Object> errorMap = objectMapper.readValue(result, Map.class);
        assertThat(errorMap).containsEntry("error", "GEOCODING_ERROR");
    }

    @Test
    @DisplayName("Geocode tool should return generic error on unexpected exception")
    void geocode_withUnexpectedException_shouldReturnGenericError() throws Exception {
        // Arrange
        when(geocoderService.handleGeocodeRequest(anyString(), anyInt()))
            .thenThrow(new RuntimeException("Unexpected error"));

        // Act
        String result = tripSearchTool.geocode("test", 5);

        // Assert
        assertThat(result).contains("ERROR");
        assertThat(result).contains("An unexpected error occurred");

        Map<String, Object> errorMap = objectMapper.readValue(result, Map.class);
        assertThat(errorMap).containsEntry("error", "ERROR");
    }


    // ==================== Error Serialization Tests ====================

    @Test
    @DisplayName("Should serialize all error response types to valid JSON")
    void errorResponses_shouldSerializeCorrectly() throws Exception {
        // This test verifies the toErrorJson method works for all error types
        // by triggering different exceptions

        // Note: ValidationException from OtpSearchService actually validates before calling
        // the mocked method, so we test with invalid input that passes to the service

        // Geocoding error
        when(otpSearchService.handleTripRequest(anyString(), anyString(), any(), any(), any()))
            .thenThrow(new GeocodingException("test", "test message"));

        String geocodingResult = tripSearchTool.trip("from", "to", null, null, 3);
        assertThatCode(() -> objectMapper.readValue(geocodingResult, Map.class))
            .doesNotThrowAnyException();
        assertThat(geocodingResult).contains("GEOCODING_ERROR");

        // Reset the mock to avoid interference with next mock setup
        reset(otpSearchService);

        // Trip planning error
        when(otpSearchService.handleTripRequest(anyString(), anyString(), any(), any(), any()))
            .thenThrow(new TripPlanningException("test message"));

        String tripResult = tripSearchTool.trip("from", "to", null, null, 3);
        assertThatCode(() -> objectMapper.readValue(tripResult, Map.class))
            .doesNotThrowAnyException();
        assertThat(tripResult).contains("TRIP_PLANNING_ERROR");
    }

    // ==================== Departures Tool Success Tests ====================

    @Test
    @DisplayName("Departures tool should return valid JSON on success")
    void departures_withValidInputs_shouldReturnJSON() throws Exception {
        // Arrange
        when(geocoderService.resolveStopId(anyString()))
            .thenReturn("NSR:StopPlace:337");

        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("stopPlace", Map.of(
            "id", "NSR:StopPlace:337",
            "name", "Oslo S",
            "estimatedCalls", List.of()
        ));

        when(otpSearchService.handleDepartureBoardRequest(anyString(), any(), any(), any(), any()))
            .thenReturn(mockResponse);

        // Act
        String result = tripSearchTool.departures("Oslo S", 10, null, null, null);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).contains("stopPlace");
        assertThat(result).doesNotContain("error");

        // Verify it's valid JSON
        assertThatCode(() -> objectMapper.readTree(result))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Departures tool should work with NSR ID directly")
    void departures_withNsrId_shouldReturnJSON() throws Exception {
        // Arrange
        when(geocoderService.resolveStopId("NSR:StopPlace:337"))
            .thenReturn("NSR:StopPlace:337");

        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("stopPlace", Map.of(
            "id", "NSR:StopPlace:337",
            "name", "Oslo S",
            "estimatedCalls", List.of()
        ));

        when(otpSearchService.handleDepartureBoardRequest(anyString(), any(), any(), any(), any()))
            .thenReturn(mockResponse);

        // Act
        String result = tripSearchTool.departures("NSR:StopPlace:337", 10, null, null, null);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).contains("stopPlace");
        assertThat(result).doesNotContain("error");
    }

    // ==================== Departures Tool Error Handling Tests ====================

    @Test
    @DisplayName("Departures tool should return error on geocoding failure")
    void departures_withGeocodingError_shouldReturnErrorResponse() throws Exception {
        // Arrange
        when(geocoderService.resolveStopId(anyString()))
            .thenThrow(new GeocodingException("Unknown Stop", "Location not found"));

        // Act
        String result = tripSearchTool.departures("Unknown Stop", 10, null, null, null);

        // Assert
        assertThat(result).contains("GEOCODING_ERROR");
        assertThat(result).contains("Unknown Stop");
        assertThat(result).contains("Location not found");

        Map<String, Object> errorMap = objectMapper.readValue(result, Map.class);
        assertThat(errorMap).containsEntry("error", "GEOCODING_ERROR");
        assertThat(errorMap).containsEntry("type", "GeocodingException");
    }

    @Test
    @DisplayName("Departures tool should return error on validation failure")
    void departures_withValidationError_shouldReturnErrorResponse() throws Exception {
        // Arrange
        when(geocoderService.resolveStopId(anyString()))
            .thenThrow(new ValidationException("stop", "stop cannot be null or empty"));

        // Act
        String result = tripSearchTool.departures("", 10, null, null, null);

        // Assert
        assertThat(result).contains("VALIDATION_ERROR");
        assertThat(result).contains("stop");

        Map<String, Object> errorMap = objectMapper.readValue(result, Map.class);
        assertThat(errorMap).containsEntry("error", "VALIDATION_ERROR");
        assertThat(errorMap).containsEntry("type", "ValidationException");
    }

    @Test
    @DisplayName("Departures tool should return error on trip planning exception")
    void departures_withTripPlanningException_shouldReturnErrorResponse() throws Exception {
        // Arrange
        when(geocoderService.resolveStopId(anyString()))
            .thenReturn("NSR:StopPlace:337");

        when(otpSearchService.handleDepartureBoardRequest(anyString(), any(), any(), any(), any()))
            .thenThrow(new TripPlanningException("API unavailable"));

        // Act
        String result = tripSearchTool.departures("Oslo S", 10, null, null, null);

        // Assert
        assertThat(result).contains("TRIP_PLANNING_ERROR");
        assertThat(result).contains("API unavailable");

        Map<String, Object> errorMap = objectMapper.readValue(result, Map.class);
        assertThat(errorMap).containsEntry("error", "TRIP_PLANNING_ERROR");
        assertThat(errorMap).containsEntry("type", "TripPlanningException");
    }

    @Test
    @DisplayName("Departures tool should return generic error on unexpected exception")
    void departures_withUnexpectedException_shouldReturnGenericError() throws Exception {
        // Arrange
        when(geocoderService.resolveStopId(anyString()))
            .thenThrow(new RuntimeException("Unexpected error"));

        // Act
        String result = tripSearchTool.departures("Oslo S", 10, null, null, null);

        // Assert
        assertThat(result).contains("ERROR");
        assertThat(result).contains("An unexpected error occurred");
        assertThat(result).contains("Unexpected error");

        Map<String, Object> errorMap = objectMapper.readValue(result, Map.class);
        assertThat(errorMap).containsEntry("error", "ERROR");
        assertThat(errorMap).containsEntry("type", "Exception");
    }
}
