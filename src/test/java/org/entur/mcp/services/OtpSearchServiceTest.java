package org.entur.mcp.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    private static JsonNode requestJson(RecordedRequest request) throws IOException {
        assert request.getBody() != null;
        return new ObjectMapper().readTree(request.getBody().utf8());
    }

    private static JsonNode variables(RecordedRequest request) throws IOException {
        return requestJson(request).get("variables");
    }

    // ==================== Next-departure look-ahead ====================

    private static final String EMPTY_TRIP = "{\"data\":{\"trip\":{\"tripPatterns\":[]}}}";

    private void geocodeOsloToAsker() {
        when(geocoderService.geocodeIfNeeded("Oslo S")).thenReturn(TestFixtures.createOsloLocation());
        when(geocoderService.geocodeIfNeeded("Asker")).thenReturn(TestFixtures.createAskerLocation());
    }

    @Test
    @DisplayName("An empty timed search looks ahead and reports when service resumes")
    void handleTripRequest_withNoTripsAtRequestedTime_shouldReportNextDeparture() throws Exception {
        geocodeOsloToAsker();
        mockWebServer.enqueue(new MockResponse.Builder().code(200).body(EMPTY_TRIP).build());
        mockWebServer.enqueue(new MockResponse.Builder().code(200).body(
            "{\"data\":{\"trip\":{\"tripPatterns\":"
            + "[{\"expectedStartTime\":\"2026-08-20T05:22:28+02:00\"}]}}}").build());

        Map<String, Object> result = otpSearchService.handleTripRequest(
            "Oslo S", "Asker", "2026-08-20T03:15:00", null, 3, null);

        assertThat(result).containsEntry("nextDepartureTime", "2026-08-20T05:22:28+02:00");

        // OTP's default window is short and adaptive, so the look-ahead has to ask for an
        // explicit one or it just returns nothing again.
        mockWebServer.takeRequest();
        RecordedRequest lookAhead = mockWebServer.takeRequest();
        assertThat(variables(lookAhead).get("searchWindow").asInt()).isEqualTo(1440);
    }

    @Test
    @DisplayName("No look-ahead when the search had no time, since it already started from now")
    void handleTripRequest_withNoTripsAndNoTime_shouldNotLookAhead() {
        geocodeOsloToAsker();
        mockWebServer.enqueue(new MockResponse.Builder().code(200).body(EMPTY_TRIP).build());

        Map<String, Object> result = otpSearchService.handleTripRequest(
            "Oslo S", "Asker", null, null, 3, null);

        assertThat(result).doesNotContainKey("nextDepartureTime");
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Nothing within the window leaves the empty result untouched")
    void handleTripRequest_withNothingAhead_shouldOmitNextDeparture() {
        geocodeOsloToAsker();
        mockWebServer.enqueue(new MockResponse.Builder().code(200).body(EMPTY_TRIP).build());
        mockWebServer.enqueue(new MockResponse.Builder().code(200).body(EMPTY_TRIP).build());

        Map<String, Object> result = otpSearchService.handleTripRequest(
            "Oslo S", "Asker", "2026-08-20T03:15:00", null, 3, null);

        assertThat(result).containsKey("trip").doesNotContainKey("nextDepartureTime");
    }

    @Test
    @DisplayName("A failing look-ahead must not turn a valid empty result into an error")
    void handleTripRequest_withFailingLookAhead_shouldStillReturnTheEmptyResult() {
        geocodeOsloToAsker();
        mockWebServer.enqueue(new MockResponse.Builder().code(200).body(EMPTY_TRIP).build());
        mockWebServer.enqueue(new MockResponse.Builder().code(500).body("upstream exploded").build());

        Map<String, Object> result = otpSearchService.handleTripRequest(
            "Oslo S", "Asker", "2026-08-20T03:15:00", null, 3, null);

        assertThat(result).containsKey("trip").doesNotContainKey("nextDepartureTime");
    }

    @Test
    @DisplayName("A search that found trips does not pay for a second request")
    void handleTripRequest_withTrips_shouldNotLookAhead() {
        geocodeOsloToAsker();
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200).body(TestFixtures.createOtpTripResponse()).build());

        otpSearchService.handleTripRequest("Oslo S", "Asker", "2026-08-20T09:00:00", null, 3, null);

        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    // ==================== Validation Tests ====================

    @Test
    @DisplayName("Should throw ValidationException when from is null")
    void handleTripRequest_withNullFrom_shouldThrowValidationException() {
        assertThatThrownBy(() ->
            otpSearchService.handleTripRequest(null, "Asker", null, null, 3, null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("from cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw ValidationException when to is null")
    void handleTripRequest_withNullTo_shouldThrowValidationException() {
        assertThatThrownBy(() ->
            otpSearchService.handleTripRequest("Oslo", null, null, null, 3, null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("to cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw ValidationException when both times are set")
    void handleTripRequest_withBothDateTimes_shouldThrowValidationException() {
        assertThatThrownBy(() ->
            otpSearchService.handleTripRequest("Oslo", "Asker",
                "2023-05-26T10:00:00", "2023-05-26T14:00:00", 3, null))
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
            "Oslo S", "Asker", null, null, 3, null);

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
        otpSearchService.handleTripRequest("Oslo", "Asker", "2023-05-26T10:00:00", null, 3, null);

        // Assert
        JsonNode variables = variables(mockWebServer.takeRequest());

        assertThat(variables.get("dateTime").asText()).isEqualTo("2023-05-26T10:00:00");
        assertThat(variables.has("arriveBy")).isFalse();
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
        otpSearchService.handleTripRequest("Oslo", "Asker", null, "2023-05-26T14:00:00", 3, null);

        // Assert
        JsonNode variables = variables(mockWebServer.takeRequest());

        assertThat(variables.get("arriveBy").asBoolean()).isTrue();
        assertThat(variables.get("dateTime").asText()).isEqualTo("2023-05-26T14:00:00");
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
        otpSearchService.handleTripRequest("Oslo", "Asker", null, null, null, null);

        // Assert
        assertThat(variables(mockWebServer.takeRequest()).get("numTripPatterns").asInt()).isEqualTo(3);
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
            otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3, null))
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
            otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3, null))
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
            otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3, null))
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
            .body(TestFixtures.createOtpErrorResponse("Invalid query at otp-internal-7:8080")).build());

        // Act & Assert: the upstream error is logged, not handed to the caller
        assertThatThrownBy(() ->
            otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3, null))
            .isInstanceOf(TripPlanningException.class)
            .hasMessage("Trip planning query failed");
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
            otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3, null))
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
            otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3, null))
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
        otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3, null);

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
        otpSearchService.handleTripRequest("Oslo S", "Asker", null, null, 3, null);

        // Assert
        JsonNode variables = variables(mockWebServer.takeRequest());

        JsonNode from = variables.get("from").get("coordinates");
        JsonNode to = variables.get("to").get("coordinates");
        assertThat(from.get("latitude").asDouble()).isEqualTo(oslo.getLatitude());
        assertThat(from.get("longitude").asDouble()).isEqualTo(oslo.getLongitude());
        assertThat(to.get("latitude").asDouble()).isEqualTo(asker.getLatitude());
        assertThat(to.get("longitude").asDouble()).isEqualTo(asker.getLongitude());
    }

    @Test
    @DisplayName("Stop IDs go to OTP as place, without geocoding")
    void handleTripRequest_withStopIds_shouldPassThemAsPlace() throws Exception {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createOtpTripResponse()).build());

        otpSearchService.handleTripRequest(
            "NSR:StopPlace:63632", "NSR:Quay:109848", null, null, 3, null);

        // The geocoder searches free text and finds nothing for an ID
        verifyNoInteractions(geocoderService);
        JsonNode variables = variables(mockWebServer.takeRequest());
        assertThat(variables.get("from").get("place").asText()).isEqualTo("NSR:StopPlace:63632");
        assertThat(variables.get("from").has("coordinates")).isFalse();
        assertThat(variables.get("to").get("place").asText()).isEqualTo("NSR:Quay:109848");
    }

    @Test
    @DisplayName("A malformed stop ID is rejected rather than geocoded")
    void handleTripRequest_withMalformedStopId_shouldThrowValidationException() {
        assertThatThrownBy(() -> otpSearchService.handleTripRequest(
            "NSR:StopPlace:abc", "Asker", null, null, 3, null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Invalid stop ID");
        verifyNoInteractions(geocoderService);
    }

    @Test
    @DisplayName("A geocoded stop is sent by its ID, with the name as a label")
    void handleTripRequest_withGeocodedStop_shouldSendStopIdAndName() throws Exception {
        when(geocoderService.geocodeIfNeeded("Hirtshals"))
            .thenReturn(new Location("Hirtshals Fjordlineterminalen", 57.596282, 9.974052,
                "NSR:StopPlace:63632"));
        when(geocoderService.geocodeIfNeeded("Asker")).thenReturn(TestFixtures.createAskerLocation());
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createOtpTripResponse()).build());

        otpSearchService.handleTripRequest("Hirtshals", "Asker", null, null, 3, null);

        JsonNode variables = variables(mockWebServer.takeRequest());
        JsonNode from = variables.get("from");
        assertThat(from.get("place").asText()).isEqualTo("NSR:StopPlace:63632");
        assertThat(from.get("name").asText()).isEqualTo("Hirtshals Fjordlineterminalen");
        assertThat(from.has("coordinates")).isTrue();
        // Not a stop: no place, so OTP routes from the coordinates
        assertThat(variables.get("to").has("place")).isFalse();
        assertThat(variables.get("to").get("name").asText()).isEqualTo("Asker");
    }

    @Test
    @DisplayName("OTP's locationNotFound becomes an error naming the location, not an empty result")
    void handleTripRequest_withLocationNotFound_shouldThrowNamingTheLocation() {
        geocodeOsloToAsker();
        mockWebServer.enqueue(new MockResponse.Builder().code(200).body(
            "{\"data\":{\"trip\":{\"routingErrors\":[{\"code\":\"locationNotFound\","
            + "\"inputField\":\"to\"}],\"tripPatterns\":[]}}}").build());

        assertThatThrownBy(() -> otpSearchService.handleTripRequest(
            "Oslo S", "Asker", "2026-08-20T03:15:00", null, 3, null))
            .isInstanceOf(TripPlanningException.class)
            .hasMessageContaining("route to 'Asker'")
            .hasMessageContaining("NSR:StopPlace");
        // No look-ahead: it would fail the same way
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Other routing errors are left out of the result")
    void handleTripRequest_withOtherRoutingErrors_shouldDropThem() {
        geocodeOsloToAsker();
        mockWebServer.enqueue(new MockResponse.Builder().code(200).body(
            "{\"data\":{\"trip\":{\"routingErrors\":[{\"code\":\"noTransitConnection\","
            + "\"inputField\":null}],\"tripPatterns\":[]}}}").build());

        Map<String, Object> result = otpSearchService.handleTripRequest(
            "Oslo S", "Asker", null, null, 3, null);

        assertThat((Map<String, Object>) result.get("trip")).doesNotContainKey("routingErrors");
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
        otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3, null);

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
        otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3, null);

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
        otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3, null);

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

    @Test
    @DisplayName("Should reject an invalid transport mode before calling OTP")
    void handleDepartureBoardRequest_withInvalidMode_shouldThrowValidationException() {
        assertThatThrownBy(() ->
            otpSearchService.handleDepartureBoardRequest(
                "NSR:StopPlace:337", 10, null, 60, List.of("hovercraft")))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("hovercraft");
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

        JsonNode severities = variables(mockWebServer.takeRequest()).get("severities");
        assertThat(severities).extracting(JsonNode::asText).containsExactly("normal", "severe");
    }

    @Test
    @DisplayName("Should not include severities filter when severities is null")
    void handleSituationsRequest_withNullSeverities_shouldOmitFilter() throws Exception {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createSituationsResponse()).build());

        otpSearchService.handleSituationsRequest(null);

        // Left out entirely rather than sent as null, so OTP applies no severity filter.
        assertThat(variables(mockWebServer.takeRequest()).has("severities")).isFalse();
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
        JsonNode variables = variables(request);
        assertThat(variables.get("latitude").asDouble()).isEqualTo(59.911);
        assertThat(variables.get("longitude").asDouble()).isEqualTo(10.748);
        assertThat(variables.get("maximumDistance").asDouble()).isEqualTo(500.0);
        assertThat(variables.get("maximumResults").asInt()).isEqualTo(5);
        assertThat(requestJson(request).get("query").asText()).contains("filterByPlaceTypes: [stopPlace]");
    }

    @Test
    @DisplayName("Should include mode filter in query when transportModes provided")
    void handleNearbyStopsRequest_withModeFilter_shouldIncludeInQuery() throws Exception {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createNearestStopsResponse()).build());

        otpSearchService.handleNearbyStopsRequest(59.911, 10.748, 500, 10, List.of("rail", "metro"));

        JsonNode modes = variables(mockWebServer.takeRequest()).get("filterByModes");
        assertThat(modes).extracting(JsonNode::asText).containsExactly("rail", "metro");
    }

    @Test
    @DisplayName("Should reject an invalid transport mode before calling OTP")
    void handleNearbyStopsRequest_withInvalidMode_shouldThrowValidationException() {
        assertThatThrownBy(() ->
            otpSearchService.handleNearbyStopsRequest(59.911, 10.748, 500, 10, List.of("hovercraft")))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("hovercraft");
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

        otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 1, null);

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

    // ==================== Transport Modes Filter Tests ====================

    @Test
    @DisplayName("Should emit no modes block when transportModes is null")
    void handleTripRequest_withoutModes_emitsNoModesBlock() throws Exception {
        when(geocoderService.geocodeIfNeeded(anyString()))
            .thenReturn(TestFixtures.createOsloLocation());
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200).body(TestFixtures.createOtpTripResponse()).build());

        otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3, null);

        assertThat(variables(mockWebServer.takeRequest()).has("modes")).isFalse();
    }

    @Test
    @DisplayName("Should send the same query text whatever the inputs, with values only in variables")
    void handleTripRequest_queryTextIsIndependentOfInputs() throws Exception {
        when(geocoderService.geocodeIfNeeded(anyString()))
            .thenReturn(TestFixtures.createOsloLocation());
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200).body(TestFixtures.createOtpTripResponse()).build());
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200).body(TestFixtures.createOtpTripResponse()).build());

        otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3, null);
        otpSearchService.handleTripRequest("Oslo", "Asker", null, "2023-05-26T14:00:00", 7,
            List.of("rail", "bus"));

        String unfiltered = requestJson(mockWebServer.takeRequest()).get("query").asText();
        String filtered = requestJson(mockWebServer.takeRequest()).get("query").asText();
        assertThat(filtered).isEqualTo(unfiltered);
    }

    @Test
    @DisplayName("A stop ID that tries to break out of its string stays a plain variable value")
    void handleDepartureBoardRequest_withHostileStopId_doesNotReachQueryText() throws Exception {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200).body(TestFixtures.createDepartureBoardResponse()).build());
        String hostile = "NSR:StopPlace:1\") { id } hijacked: stopPlace(id: \"2";

        otpSearchService.handleDepartureBoardRequest(hostile, 10, null, 60, null);

        JsonNode request = requestJson(mockWebServer.takeRequest());
        assertThat(request.get("variables").get("id").asText()).isEqualTo(hostile);
        assertThat(request.get("query").asText()).doesNotContain("hijacked").contains("stopPlace(id: $id)");
    }

    @Test
    @DisplayName("Should emit all four mode fields when transportModes is provided")
    void handleTripRequest_withModes_emitsStreetModesAndTransportModes() throws Exception {
        when(geocoderService.geocodeIfNeeded(anyString()))
            .thenReturn(TestFixtures.createOsloLocation());
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200).body(TestFixtures.createOtpTripResponse()).build());

        otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3, List.of("rail"));

        JsonNode modes = variables(mockWebServer.takeRequest()).get("modes");

        // All four are mandatory: omitting the street modes returns zero trip patterns.
        assertThat(modes.get("accessMode").asText()).isEqualTo("foot");
        assertThat(modes.get("egressMode").asText()).isEqualTo("foot");
        assertThat(modes.get("directMode").asText()).isEqualTo("foot");
        assertThat(modes.get("transportModes").toString()).isEqualTo("[{\"transportMode\":\"rail\"}]");
    }

    @Test
    @DisplayName("Should emit multiple transport modes as separate entries")
    void handleTripRequest_withMultipleModes_emitsEachEntry() throws Exception {
        when(geocoderService.geocodeIfNeeded(anyString()))
            .thenReturn(TestFixtures.createOsloLocation());
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200).body(TestFixtures.createOtpTripResponse()).build());

        otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3, List.of("rail", "bus"));

        assertThat(variables(mockWebServer.takeRequest()).get("modes").get("transportModes").toString())
            .isEqualTo("[{\"transportMode\":\"rail\"},{\"transportMode\":\"bus\"}]");
    }

    @Test
    @DisplayName("Should reject an invalid transport mode before calling OTP")
    void handleTripRequest_withInvalidMode_shouldThrowValidationException() {
        assertThatThrownBy(() ->
            otpSearchService.handleTripRequest("Oslo", "Asker", null, null, 3, List.of("hovercraft")))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("hovercraft");
    }

    @Test
    @DisplayName("A stalled OTP times out instead of holding the thread")
    void handleDepartureBoardRequest_stalledUpstream_timesOut() {
        OtpSearchService impatient = new OtpSearchService(
            mockWebServer.url("/graphql").toString(), "test-client", geocoderService,
            Duration.ofMillis(200));
        mockWebServer.enqueue(new MockResponse.Builder().code(200).headersDelay(5, TimeUnit.SECONDS).body("{}").build());

        assertThatThrownBy(() ->
            impatient.handleDepartureBoardRequest("NSR:StopPlace:337", 5, null, 60, null))
            .isInstanceOf(TripPlanningException.class)
            .hasRootCauseInstanceOf(HttpTimeoutException.class);
    }
}
