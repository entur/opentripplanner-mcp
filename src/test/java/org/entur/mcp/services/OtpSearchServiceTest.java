package org.entur.mcp.services;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.entur.mcp.TestFixtures;
import org.entur.mcp.exception.TripPlanningException;
import org.entur.mcp.exception.ValidationException;
import org.entur.mcp.model.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OtpSearchService Unit Tests")
class OtpSearchServiceTest {

    @Mock
    private GeocoderService geocoderService;

    private MockWebServer mockWebServer;
    private OtpSearchService otpSearchService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String otpUrl = mockWebServer.url("/graphql").toString();
        otpSearchService = new OtpSearchService(otpUrl, "test-client", geocoderService);
    }

    @AfterEach
    void tearDown() {
        mockWebServer.close();
    }

    // ==================== Validation Tests ====================

    @Test
    @DisplayName("Should throw ValidationException when from is null")
    void handleTripRequest_withNullFrom_shouldThrowValidationException() {
        assertThatThrownBy(() ->
            otpSearchService.handleTripRequest(null, "Asker", null, null, 3))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("from cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw ValidationException when to is null")
    void handleTripRequest_withNullTo_shouldThrowValidationException() {
        assertThatThrownBy(() ->
            otpSearchService.handleTripRequest("Oslo", null, null, null, 3))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("to cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw ValidationException when both times are set")
    void handleTripRequest_withBothDateTimes_shouldThrowValidationException() {
        assertThatThrownBy(() ->
            otpSearchService.handleTripRequest("Oslo", "Asker",
                "2023-05-26T10:00:00", "2023-05-26T14:00:00", 3))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Cannot specify both departureTime and arrivalTime");
    }

    // ==================== Success Cases ====================

    @Test
    @DisplayName("Should successfully plan trip with valid inputs")
    void handleTripRequest_withValidInputs_shouldReturnTripData() {
        // Arrange
        when(geocoderService.geocodeIfNeeded("Oslo S"))
            .thenReturn(TestFixtures.createOsloLocation());
        when(geocoderService.geocodeIfNeeded("Asker"))
            .thenReturn(TestFixtures.createAskerLocation());

        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createOtpTripResponse()).build());

        // Act
        Map<String, Object> result = otpSearchService.handleTripRequest(
            "Oslo S", "Asker", null, null, 3);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsKey("trip");

        verify(geocoderService).geocodeIfNeeded("Oslo S");
        verify(geocoderService).geocodeIfNeeded("Asker");
    }

    @Test
    @DisplayName("Should include departure time in GraphQL query")
    void handleTripRequest_withDepartureTime_shouldIncludeInQuery() throws Exception {
        // Arrange
        when(geocoderService.geocodeIfNeeded(anyString()))
            .thenReturn(TestFixtures.createOsloLocation());

        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createOtpTripResponse()).build());

        // Act
        otpSearchService.handleTripRequest("Oslo", "Asker", "2023-05-26T10:00:00", null, 3);

        // Assert
        RecordedRequest request = mockWebServer.takeRequest();
        assert request.getBody() != null;
        String requestBody = request.getBody().utf8();

        assertThat(requestBody).contains("dateTime: \\\"2023-05-26T10:00:00\\\"");
        assertThat(requestBody).doesNotContain("arriveBy");
    }

    @Test
    @DisplayName("Should include arrival time with arriveBy in GraphQL query")
    void handleTripRequest_withArrivalTime_shouldIncludeArriveByInQuery() throws Exception {
        // Arrange
        when(geocoderService.geocodeIfNeeded(anyString()))
            .thenReturn(TestFixtures.createOsloLocation());

        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createOtpTripResponse()).build());

        // Act
        otpSearchService.handleTripRequest("Oslo", "Asker", null, "2023-05-26T14:00:00", 3);

        // Assert
        RecordedRequest request = mockWebServer.takeRequest();
        assert request.getBody() != null;
        String requestBody = request.getBody().utf8();

        assertThat(requestBody).contains("arriveBy: true");
        assertThat(requestBody).contains("dateTime: \\\"2023-05-26T14:00:00\\\"");
    }

    @Test
    @DisplayName("Should use default maxResults when null")
    void handleTripRequest_withNullMaxResults_shouldUseDefault() throws Exception {
        // Arrange
        when(geocoderService.geocodeIfNeeded(anyString()))
            .thenReturn(TestFixtures.createOsloLocation());

        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createOtpTripResponse()).build());

        // Act
        otpSearchService.handleTripRequest("Oslo", "Asker", null, null, null);

        // Assert
        RecordedRequest request = mockWebServer.takeRequest();
        assert request.getBody() != null;
        String requestBody = request.getBody().utf8();

        assertThat(requestBody).contains("numTripPatterns: 3");
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Should throw TripPlanningException when geocoding 'from' fails")
    void handleTripRequest_withGeocodingFromFailure_shouldThrowTripPlanningException() {
        // Arrange
        when(geocoderService.geocodeIfNeeded("Oslo"))
            .thenThrow(new RuntimeException("Geocoding failed"));

        // Act & Assert
        assertThatThrownBy(() ->
            otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3))
            .isInstanceOf(TripPlanningException.class)
            .hasMessageContaining("Failed to geocode location");
    }

    @Test
    @DisplayName("Should throw TripPlanningException when geocoding 'to' fails")
    void handleTripRequest_withGeocodingToFailure_shouldThrowTripPlanningException() {
        // Arrange
        when(geocoderService.geocodeIfNeeded("Oslo"))
            .thenReturn(TestFixtures.createOsloLocation());
        when(geocoderService.geocodeIfNeeded("Asker"))
            .thenThrow(new RuntimeException("Geocoding failed"));

        // Act & Assert
        assertThatThrownBy(() ->
            otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3))
            .isInstanceOf(TripPlanningException.class)
            .hasMessageContaining("Failed to geocode location");
    }

    @Test
    @DisplayName("Should throw TripPlanningException on 500 response")
    void handleTripRequest_with500Response_shouldThrowTripPlanningException() {
        // Arrange
        when(geocoderService.geocodeIfNeeded(anyString()))
            .thenReturn(TestFixtures.createOsloLocation());

        mockWebServer.enqueue(new MockResponse.Builder()
            .code(500)
            .body("Internal Server Error").build());

        // Act & Assert
        assertThatThrownBy(() ->
            otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3))
            .isInstanceOf(TripPlanningException.class)
            .hasMessageContaining("status 500");
    }

    @Test
    @DisplayName("Should throw TripPlanningException when GraphQL returns errors")
    void handleTripRequest_withGraphQLErrors_shouldThrowTripPlanningException() {
        // Arrange
        when(geocoderService.geocodeIfNeeded(anyString()))
            .thenReturn(TestFixtures.createOsloLocation());

        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createOtpErrorResponse("Invalid query")).build());

        // Act & Assert
        assertThatThrownBy(() ->
            otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3))
            .isInstanceOf(TripPlanningException.class)
            .hasMessageContaining("Trip planning query failed");
    }

    @Test
    @DisplayName("Should throw TripPlanningException when response has no data")
    void handleTripRequest_withNoData_shouldThrowTripPlanningException() {
        // Arrange
        when(geocoderService.geocodeIfNeeded(anyString()))
            .thenReturn(TestFixtures.createOsloLocation());

        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body("{}").build());

        // Act & Assert
        assertThatThrownBy(() ->
            otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3))
            .isInstanceOf(TripPlanningException.class)
            .hasMessageContaining("No trip data returned from API");
    }

    @Test
    @DisplayName("Should throw TripPlanningException on invalid JSON response")
    void handleTripRequest_withInvalidJSON_shouldThrowTripPlanningException() {
        // Arrange
        when(geocoderService.geocodeIfNeeded(anyString()))
            .thenReturn(TestFixtures.createOsloLocation());

        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body("This is not JSON").build());

        // Act & Assert
        assertThatThrownBy(() ->
            otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3))
            .isInstanceOf(TripPlanningException.class)
            .hasMessageContaining("Invalid response format");
    }

    @Test
    @DisplayName("Should include ET-Client-Name header in request")
    void handleTripRequest_shouldIncludeClientNameHeader() throws Exception {
        // Arrange
        when(geocoderService.geocodeIfNeeded(anyString()))
            .thenReturn(TestFixtures.createOsloLocation());

        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createOtpTripResponse()).build());

        // Act
        otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3);

        // Assert
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getHeaders().get("ET-Client-Name")).isEqualTo("test-client");
        assertThat(request.getHeaders().get("Content-Type")).isEqualTo("application/json");
    }

    @Test
    @DisplayName("Should include coordinates in GraphQL query")
    void handleTripRequest_shouldIncludeCoordinatesInQuery() throws Exception {
        // Arrange
        Location oslo = TestFixtures.createOsloLocation();
        Location asker = TestFixtures.createAskerLocation();

        when(geocoderService.geocodeIfNeeded("Oslo S")).thenReturn(oslo);
        when(geocoderService.geocodeIfNeeded("Asker")).thenReturn(asker);

        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createOtpTripResponse()).build());

        // Act
        otpSearchService.handleTripRequest("Oslo S", "Asker", null, null, 3);

        // Assert
        RecordedRequest request = mockWebServer.takeRequest();
        assert request.getBody() != null;
        String requestBody = request.getBody().utf8();

        assertThat(requestBody).contains("latitude: " + oslo.getLatitude());
        assertThat(requestBody).contains("longitude: " + oslo.getLongitude());
        assertThat(requestBody).contains("latitude: " + asker.getLatitude());
        assertThat(requestBody).contains("longitude: " + asker.getLongitude());
    }
}
