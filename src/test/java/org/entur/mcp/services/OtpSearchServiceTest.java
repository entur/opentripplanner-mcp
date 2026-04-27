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
import java.util.List;
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

    @Test
    @DisplayName("Should include leg geometry and intermediate stops in GraphQL query")
    void handleTripRequest_queryIncludesGeometryAndIntermediateStops() throws Exception {
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
        assert request.getBody() != null;
        String requestBody = request.getBody().utf8();

        assertThat(requestBody).contains("pointsOnLink");
        assertThat(requestBody).contains("points");
        assertThat(requestBody).contains("intermediateEstimatedCalls");
        assertThat(requestBody).contains("quay");
    }

    @Test
    @DisplayName("Should include destinationDisplay frontText in GraphQL query")
    void handleTripRequest_queryIncludesDestinationDisplay() throws Exception {
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
        assert request.getBody() != null;
        String requestBody = request.getBody().utf8();

        assertThat(requestBody).contains("destinationDisplay");
        assertThat(requestBody).contains("frontText");
    }

    @Test
    @DisplayName("Should include serviceJourney id in trip legs GraphQL query")
    void handleTripRequest_queryIncludesServiceJourneyId() throws Exception {
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
        assert request.getBody() != null;
        String requestBody = request.getBody().utf8();

        assertThat(requestBody).contains("serviceJourney");
        assertThat(requestBody).containsPattern("serviceJourney\\s*\\{[^}]*\\bid\\b");
    }

    @Test
    @DisplayName("Should successfully fetch departure board with valid inputs")
    void handleDepartureBoardRequest_withValidInputs_shouldReturnDepartureData() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createDepartureBoardResponse()).build());

        Map<String, Object> result = otpSearchService.handleDepartureBoardRequest(
            "NSR:StopPlace:337", 10, null, 60, null);

        assertThat(result).isNotNull();
        assertThat(result).containsKey("stopPlace");
    }

    @Test
    @DisplayName("Should include arrivals and departures aliases in GraphQL query")
    void handleDepartureBoardRequest_queryIncludesArrivalsAndDepartures() throws Exception {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createDepartureBoardResponse()).build());

        otpSearchService.handleDepartureBoardRequest("NSR:StopPlace:337", 10, null, 60, null);

        RecordedRequest request = mockWebServer.takeRequest();
        assert request.getBody() != null;
        String requestBody = request.getBody().utf8();

        assertThat(requestBody).contains("arrivals:");
        assertThat(requestBody).contains("departures:");
        assertThat(requestBody).contains("arrivalDeparture: arrivals");
        assertThat(requestBody).contains("arrivalDeparture: departures");
    }

    // ==================== Situations Tests ====================

    @Test
    @DisplayName("Should return situations data on success")
    void handleSituationsRequest_withNoFilter_shouldReturnData() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createSituationsResponse()).build());

        Map<String, Object> result = otpSearchService.handleSituationsRequest(null);

        assertThat(result).isNotNull();
        assertThat(result).containsKey("situations");
    }

    @Test
    @DisplayName("Should include severities filter in GraphQL query when provided")
    void handleSituationsRequest_withSeverityFilter_shouldIncludeInQuery() throws Exception {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createSituationsResponse()).build());

        otpSearchService.handleSituationsRequest(List.of("normal", "severe"));

        RecordedRequest request = mockWebServer.takeRequest();
        String requestBody = request.getBody().utf8();
        assertThat(requestBody).contains("severities:");
        assertThat(requestBody).contains("normal");
        assertThat(requestBody).contains("severe");
    }

    @Test
    @DisplayName("Should not include severities filter when severities is null")
    void handleSituationsRequest_withNullSeverities_shouldOmitFilter() throws Exception {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createSituationsResponse()).build());

        otpSearchService.handleSituationsRequest(null);

        RecordedRequest request = mockWebServer.takeRequest();
        String requestBody = request.getBody().utf8();
        assertThat(requestBody).contains("situations");
        assertThat(requestBody).doesNotContain("severities:");
        assertThat(requestBody).doesNotContain("situations()");
    }

    @Test
    @DisplayName("Should throw TripPlanningException on GraphQL error response")
    void handleSituationsRequest_withGraphQLError_shouldThrowException() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createOtpErrorResponse("Situations query failed")).build());

        assertThatThrownBy(() -> otpSearchService.handleSituationsRequest(null))
            .isInstanceOf(TripPlanningException.class);
    }

    @Test
    @DisplayName("Should include affects inline fragments in query")
    void handleSituationsRequest_queryIncludesAffectsFragments() throws Exception {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createSituationsResponse()).build());

        otpSearchService.handleSituationsRequest(null);

        RecordedRequest request = mockWebServer.takeRequest();
        String requestBody = request.getBody().utf8();
        assertThat(requestBody).contains("AffectedLine");
        assertThat(requestBody).contains("AffectedStopPlace");
        assertThat(requestBody).contains("validityPeriod");
    }

    // ==================== Nearby Stops Tests ====================

    @Test
    @DisplayName("Should return nearest data on success")
    void handleNearbyStopsRequest_withValidInputs_shouldReturnData() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createNearestStopsResponse()).build());

        Map<String, Object> result = otpSearchService.handleNearbyStopsRequest(
            59.911, 10.748, 500, 10, null);

        assertThat(result).isNotNull();
        assertThat(result).containsKey("nearest");
    }

    @Test
    @DisplayName("Should include coordinates and radius in GraphQL query")
    void handleNearbyStopsRequest_shouldIncludeParamsInQuery() throws Exception {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createNearestStopsResponse()).build());

        otpSearchService.handleNearbyStopsRequest(59.911, 10.748, 500, 5, null);

        RecordedRequest request = mockWebServer.takeRequest();
        String requestBody = request.getBody().utf8();
        assertThat(requestBody).contains("59.911");
        assertThat(requestBody).contains("10.748");
        assertThat(requestBody).contains("500.0");
        assertThat(requestBody).contains("maximumResults: 5");
        assertThat(requestBody).contains("stopPlace");
    }

    @Test
    @DisplayName("Should include mode filter in query when transportModes provided")
    void handleNearbyStopsRequest_withModeFilter_shouldIncludeInQuery() throws Exception {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createNearestStopsResponse()).build());

        otpSearchService.handleNearbyStopsRequest(59.911, 10.748, 500, 10, List.of("rail", "metro"));

        RecordedRequest request = mockWebServer.takeRequest();
        String requestBody = request.getBody().utf8();
        assertThat(requestBody).contains("filterByModes");
        assertThat(requestBody).contains("rail");
        assertThat(requestBody).contains("metro");
    }

    @Test
    @DisplayName("Should include estimatedCalls in the query")
    void handleNearbyStopsRequest_queryIncludesEstimatedCalls() throws Exception {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createNearestStopsResponse()).build());

        otpSearchService.handleNearbyStopsRequest(59.911, 10.748, 500, 10, null);

        RecordedRequest request = mockWebServer.takeRequest();
        String requestBody = request.getBody().utf8();
        assertThat(requestBody).contains("estimatedCalls");
        assertThat(requestBody).contains("expectedDepartureTime");
        assertThat(requestBody).contains("destinationDisplay");
    }

    @Test
    @DisplayName("Should throw TripPlanningException on GraphQL error")
    void handleNearbyStopsRequest_withGraphQLError_shouldThrowException() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createOtpErrorResponse("Nearest query failed")).build());

        assertThatThrownBy(() ->
            otpSearchService.handleNearbyStopsRequest(59.911, 10.748, 500, 10, null))
            .isInstanceOf(TripPlanningException.class);
    }

    // ==================== New Field Tests ====================

    @Test
    @DisplayName("Should include emission field in trip query")
    void handleTripRequest_queryContainsEmissionField() throws Exception {
        when(geocoderService.geocodeIfNeeded(anyString()))
            .thenReturn(TestFixtures.createOsloLocation());

        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createOtpTripResponse()).build());

        otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 1);

        RecordedRequest request = mockWebServer.takeRequest();
        assert request.getBody() != null;
        String requestBody = request.getBody().utf8();
        assertThat(requestBody).contains("emission");
        assertThat(requestBody).contains("co2");
        assertThat(requestBody).contains("fromEstimatedCall");
        assertThat(requestBody).contains("toEstimatedCall");
        assertThat(requestBody).contains("empiricalDelay");
        assertThat(requestBody).contains("p50");
        assertThat(requestBody).contains("p90");
    }

    @Test
    @DisplayName("Should include empiricalDelay field in departure board query")
    void handleDepartureBoardRequest_queryContainsEmpiricalDelay() throws Exception {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createDepartureBoardResponse()).build());

        otpSearchService.handleDepartureBoardRequest("NSR:StopPlace:337", 10, null, 60, null);

        RecordedRequest request = mockWebServer.takeRequest();
        assert request.getBody() != null;
        String requestBody = request.getBody().utf8();
        assertThat(requestBody).contains("empiricalDelay");
        assertThat(requestBody).contains("p50");
        assertThat(requestBody).contains("p90");
    }

    @Test
    @DisplayName("Should include empiricalDelay field in nearest stops query")
    void handleNearbyStopsRequest_queryContainsEmpiricalDelay() throws Exception {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createNearestStopsResponse()).build());

        otpSearchService.handleNearbyStopsRequest(59.911076, 10.748128, 500, 5, null);

        RecordedRequest request = mockWebServer.takeRequest();
        assert request.getBody() != null;
        String requestBody = request.getBody().utf8();
        assertThat(requestBody).contains("empiricalDelay");
        assertThat(requestBody).contains("p50");
        assertThat(requestBody).contains("p90");
    }
}
