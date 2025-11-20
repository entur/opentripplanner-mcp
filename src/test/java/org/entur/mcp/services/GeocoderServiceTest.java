package org.entur.mcp.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.entur.mcp.TestFixtures;
import org.entur.mcp.exception.GeocodingException;
import org.entur.mcp.exception.ValidationException;
import org.entur.mcp.model.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("GeocoderService Unit Tests")
class GeocoderServiceTest {

    private MockWebServer mockWebServer;
    private GeocoderService geocoderService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/geocoder").toString();
        geocoderService = new GeocoderService(baseUrl, "test-client");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // ==================== handleGeocodeRequest Tests ====================

    @Test
    @DisplayName("Should successfully geocode valid location")
    void handleGeocodeRequest_withValidResponse_shouldParseCorrectly() throws JsonProcessingException {
        // Arrange
        String mockResponse = TestFixtures.createGeocoderResponse("Oslo S", 59.911, 10.748);
        mockWebServer.enqueue(new MockResponse.Builder()
            .body(mockResponse)
            .addHeader("Content-Type", "application/json").build());

        // Act
        Map<String, Object> result = geocoderService.handleGeocodeRequest("Oslo S", 5);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsKey("features");

        @SuppressWarnings("unchecked")
        List<Object> features = (List<Object>) result.get("features");
        assertThat(features).hasSize(1);
    }

    @Test
    @DisplayName("Should throw ValidationException when text is null")
    void handleGeocodeRequest_withNullText_shouldThrowValidationException() {
        assertThatThrownBy(() -> geocoderService.handleGeocodeRequest(null, 5))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("text cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw ValidationException when text is empty")
    void handleGeocodeRequest_withEmptyText_shouldThrowValidationException() {
        assertThatThrownBy(() -> geocoderService.handleGeocodeRequest("", 5))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("text cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw GeocodingException on 500 response")
    void handleGeocodeRequest_with500Response_shouldThrowGeocodingException() {
        // Arrange
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(500)
            .body("Internal Server Error").build());

        // Act & Assert
        assertThatThrownBy(() -> geocoderService.handleGeocodeRequest("Oslo S", 5))
            .isInstanceOf(GeocodingException.class)
            .hasMessageContaining("status 500");
    }

    @Test
    @DisplayName("Should throw GeocodingException on invalid JSON response")
    void handleGeocodeRequest_withInvalidJSON_shouldThrowGeocodingException() {
        // Arrange
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body("This is not JSON").build());

        // Act & Assert
        assertThatThrownBy(() -> geocoderService.handleGeocodeRequest("Oslo S", 5))
            .isInstanceOf(GeocodingException.class)
            .hasMessageContaining("Invalid response format");
    }

    @Test
    @DisplayName("Should limit results to maxResults")
    void handleGeocodeRequest_shouldLimitResultsToMaxResults() throws JsonProcessingException {
        // Arrange
        String mockResponse = TestFixtures.createGeocoderResponseMultiple(10);
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(mockResponse).build());

        // Act
        Map<String, Object> result = geocoderService.handleGeocodeRequest("test", 3);

        // Assert
        @SuppressWarnings("unchecked")
        List<Object> features = (List<Object>) result.get("features");
        assertThat(features).hasSize(3);
    }

    @Test
    @DisplayName("Should throw ValidationException when maxResults is zero")
    void handleGeocodeRequest_withZeroMaxResults_shouldThrowValidationException() {
        // Act & Assert
        assertThatThrownBy(() -> geocoderService.handleGeocodeRequest("Oslo", 0))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("maxResults must be at least 1");
    }

    // ==================== geocodeIfNeeded Tests ====================

    @Test
    @DisplayName("Should parse coordinate string directly")
    void geocodeIfNeeded_withCoordinateString_shouldReturnLocation() throws JsonProcessingException {
        // Act
        Location location = geocoderService.geocodeIfNeeded("59.911,10.748");

        // Assert
        assertThat(location).isNotNull();
        assertThat(location.getLatitude()).isEqualTo(59.911, within(0.001));
        assertThat(location.getLongitude()).isEqualTo(10.748, within(0.001));
        assertThat(location.getPlace()).isEqualTo("coordinate");
    }

    @Test
    @DisplayName("Should geocode when coordinate string is invalid")
    void geocodeIfNeeded_withInvalidCoordinates_shouldGeocodeInstead() throws JsonProcessingException {
        // Arrange
        String mockResponse = TestFixtures.createGeocoderResponse("Oslo S", 59.911, 10.748);
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(mockResponse).build());

        // Act
        Location location = geocoderService.geocodeIfNeeded("Oslo S");

        // Assert
        assertThat(location).isNotNull();
        assertThat(location.getPlace()).isEqualTo("Oslo S");
    }

    @Test
    @DisplayName("Should throw GeocodingException when no results found")
    void geocodeIfNeeded_withNoResults_shouldThrowGeocodingException() {
        // Arrange
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createEmptyGeocoderResponse()).build());

        // Act & Assert
        assertThatThrownBy(() -> geocoderService.geocodeIfNeeded("Unknown Place"))
            .isInstanceOf(GeocodingException.class)
            .hasMessageContaining("No locations found")
            .hasMessageContaining("Unknown Place");
    }

    @Test
    @DisplayName("Should extract coordinates from geocoder response")
    void geocodeIfNeeded_withPlaceName_shouldExtractCoordinates() throws JsonProcessingException {
        // Arrange
        String mockResponse = TestFixtures.createGeocoderResponse("Oslo S", 59.911076, 10.748128);
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(mockResponse).build());

        // Act
        Location location = geocoderService.geocodeIfNeeded("Oslo S");

        // Assert
        assertThat(location).isNotNull();
        assertThat(location.getPlace()).isEqualTo("Oslo S");
        assertThat(location.getLatitude()).isEqualTo(59.911076, within(0.0001));
        assertThat(location.getLongitude()).isEqualTo(10.748128, within(0.0001));
    }

    @Test
    @DisplayName("Should throw ValidationException for null location")
    void geocodeIfNeeded_withNullLocation_shouldThrowValidationException() {
        assertThatThrownBy(() -> geocoderService.geocodeIfNeeded(null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("location cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw ValidationException for empty location")
    void geocodeIfNeeded_withEmptyLocation_shouldThrowValidationException() {
        assertThatThrownBy(() -> geocoderService.geocodeIfNeeded(""))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("location cannot be null or empty");
    }

    @Test
    @DisplayName("Should handle coordinate string with spaces")
    void geocodeIfNeeded_withCoordinateStringWithSpaces_shouldParse() throws JsonProcessingException {
        // Act
        Location location = geocoderService.geocodeIfNeeded("59.911 , 10.748");

        // Assert
        assertThat(location).isNotNull();
        assertThat(location.getLatitude()).isEqualTo(59.911, within(0.001));
        assertThat(location.getLongitude()).isEqualTo(10.748, within(0.001));
    }
}
