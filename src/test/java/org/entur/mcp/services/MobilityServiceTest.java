package org.entur.mcp.services;

import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.entur.mcp.TestFixtures;
import org.entur.mcp.exception.MobilityException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MobilityService Unit Tests")
class MobilityServiceTest {

    private MockWebServer mockWebServer;
    private MobilityService mobilityService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        String url = mockWebServer.url("/graphql").toString();
        mobilityService = new MobilityService(url, "test-client");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.close();
    }

    @Test
    @DisplayName("Happy path: returns vehicles and stations arrays")
    void findNearby_happyPath_returnsBothArrays() {
        mockWebServer.setDispatcher(mobilityDispatcher(
            TestFixtures.createMobilityVehiclesResponse(),
            TestFixtures.createMobilityStationsResponse()));

        Map<String, Object> result = mobilityService.findNearby(
            59.91, 10.75, 500, null, null, null, 20);

        assertThat(result).containsKeys("vehicles", "stations");
        List<?> vehicles = (List<?>) result.get("vehicles");
        List<?> stations = (List<?>) result.get("stations");
        assertThat(vehicles).hasSize(2);
        assertThat(stations).hasSize(1);
    }

    @Test
    @DisplayName("GraphQL errors field throws MobilityException")
    void findNearby_graphQlError_throws() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createMobilityErrorResponse("Range too large"))
            .build());
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createMobilityStationsResponse())
            .build());

        assertThatThrownBy(() -> mobilityService.findNearby(
            59.91, 10.75, 500, null, null, null, 20))
            .isInstanceOf(MobilityException.class)
            .hasMessageContaining("Range too large");
    }

    @Test
    @DisplayName("Non-200 status throws MobilityException")
    void findNearby_non200Status_throws() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(500)
            .body("internal server error")
            .build());
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createMobilityStationsResponse())
            .build());

        assertThatThrownBy(() -> mobilityService.findNearby(
            59.91, 10.75, 500, null, null, null, 20))
            .isInstanceOf(MobilityException.class)
            .hasMessageContaining("status 500");
    }

    @Test
    @DisplayName("Empty responses return empty arrays")
    void findNearby_emptyResponses_returnsEmptyArrays() {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createMobilityEmptyResponse())
            .build());
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createMobilityEmptyResponse())
            .build());

        Map<String, Object> result = mobilityService.findNearby(
            59.91, 10.75, 500, null, null, null, 20);

        assertThat((List<?>) result.get("vehicles")).isEmpty();
        assertThat((List<?>) result.get("stations")).isEmpty();
    }

    @Test
    @DisplayName("Results include distanceMeters and are sorted ascending")
    void findNearby_resultsHaveDistance_sortedAscending() {
        // Vehicle abc-123 at (59.9123, 10.7456) is farther from (59.91, 10.75)
        // than def-456 at (59.9150, 10.7500). After sorting, def-456 should be first.
        mockWebServer.setDispatcher(mobilityDispatcher(
            TestFixtures.createMobilityVehiclesResponse(),
            TestFixtures.createMobilityStationsResponse()));

        Map<String, Object> result = mobilityService.findNearby(
            59.91, 10.75, 500, null, null, null, 20);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> vehicles = (List<Map<String, Object>>) result.get("vehicles");
        assertThat(vehicles).hasSize(2);
        assertThat(vehicles.get(0)).containsKey("distanceMeters");
        int first = (int) vehicles.get(0).get("distanceMeters");
        int second = (int) vehicles.get(1).get("distanceMeters");
        assertThat(first).isLessThanOrEqualTo(second);
        assertThat(first).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Form factor and operator filters are sent in the GraphQL variables")
    void findNearby_filtersPassedThrough() throws Exception {
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createMobilityEmptyResponse())
            .build());
        mockWebServer.enqueue(new MockResponse.Builder()
            .code(200)
            .body(TestFixtures.createMobilityEmptyResponse())
            .build());

        mobilityService.findNearby(
            59.91, 10.75, 500,
            List.of("SCOOTER_STANDING"),
            List.of("ELECTRIC"),
            List.of("YVO:Operator:voi"),
            20);

        RecordedRequest req1 = mockWebServer.takeRequest();
        RecordedRequest req2 = mockWebServer.takeRequest();
        String body1 = req1.getBody().utf8();
        String body2 = req2.getBody().utf8();

        assertThat(body1 + body2).contains("SCOOTER_STANDING");
        assertThat(body1 + body2).contains("ELECTRIC");
        assertThat(body1 + body2).contains("YVO:Operator:voi");
    }

    // MobilityService dispatches the vehicles and stations queries in parallel, so a FIFO
    // enqueue() race-condition can give either request either response. Dispatch by query
    // operation name so the right body reaches the right future regardless of arrival order.
    private static Dispatcher mobilityDispatcher(String vehiclesBody, String stationsBody) {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String body = request.getBody().utf8();
                String responseBody = body.contains("query Vehicles") ? vehiclesBody : stationsBody;
                return new MockResponse.Builder().code(200).body(responseBody).build();
            }
        };
    }
}
