package org.entur.mcp.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import org.entur.mcp.exception.TripPlanningException;
import org.entur.mcp.metrics.MetricsUtils;
import org.entur.mcp.model.Location;
import org.entur.mcp.validation.InputValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Timed(value = "mcp.trip.service", description = "Trip request towards the OTP-service")
public class OtpSearchService {

    private static final Logger log = LoggerFactory.getLogger(OtpSearchService.class);

    private final String otpURL;

    private final String etClientName;

    private final GeocoderService geocoderService;

    private final HttpClient client;

    private static final String baseQuery = """
                {
                    trip(
                        from: {
                            place: "%s"
                            coordinates: {
                                latitude: %f
                                longitude: %f
                            }
                        }
                        to: {
                            place: "%s"
                            coordinates: {
                                latitude: %f
                                longitude: %f
                            }
                        }
                        %s
                        numTripPatterns: %d
                    ) {
                        tripPatterns {
                            duration
                            expectedStartTime
                            expectedEndTime
                            legs {
                                mode
                                distance
                                duration
                                fromPlace {
                                    name
                                    latitude
                                    longitude
                                    quay {
                                        stopPlace {
                                            id
                                        }
                                    }
                                }
                                fromEstimatedCall {
                                    occupancyStatus
                                    destinationDisplay {
                                        frontText
                                    }
                                    empiricalDelay {
                                        p50
                                        p90
                                    }
                                }
                                toPlace {
                                    name
                                    latitude
                                    longitude
                                    quay {
                                        stopPlace {
                                            id
                                        }
                                    }
                                }
                                toEstimatedCall {
                                    empiricalDelay {
                                        p50
                                        p90
                                    }
                                }
                                line {
                                    publicCode
                                    name
                                    presentation {
                                      colour
                                      textColour
                                    }
                                }
                                serviceJourney {
                                    id
                                }
                                aimedStartTime
                                expectedStartTime
                                aimedEndTime
                                expectedEndTime
                                pointsOnLink {
                                    points
                                }
                                intermediateEstimatedCalls {
                                    quay {
                                        latitude
                                        longitude
                                    }
                                }
                                situations {
                                    situationNumber
                                    severity
                                    summary {
                                        language
                                        value
                                    }
                                }
                                emission {
                                    co2
                                }
                            }
                            emission {
                                co2
                            }
                        }
                    }
                }""";

    private static final String departureBoardQuery = """
                {
                    stopPlace(id: "%s") {
                        id
                        name
                        arrivals: estimatedCalls(
                            numberOfDepartures: %d
                            %s
                            timeRange: %d
                            arrivalDeparture: arrivals
                        ) {
                            ...calls
                        }
                        departures: estimatedCalls(
                            numberOfDepartures: %d
                            %s
                            timeRange: %d
                            arrivalDeparture: departures
                        ) {
                            ...calls
                        }
                    }
                }
                fragment calls on EstimatedCall {
                  aimedDepartureTime
                  expectedDepartureTime
                  actualDepartureTime
                  aimedArrivalTime
                  expectedArrivalTime
                  cancellation
                  realtime
                  realtimeState
                  occupancyStatus
                  quay {
                    id
                    publicCode
                    name
                  }
                  destinationDisplay {
                    frontText
                  }
                  serviceJourney {
                    id
                    line {
                      id
                      publicCode
                      name
                      transportMode
                      presentation {
                        colour
                        textColour
                      }
                    }
                  }
                  situations {
                    summary {
                      value
                    }
                  }
                  empiricalDelay {
                    p50
                    p90
                  }
                }
""";

    private static final String situationsQuery = """
                {
                    situations%s {
                        id
                        situationNumber
                        severity
                        reportType
                        summary {
                            value
                            language
                        }
                        description {
                            value
                            language
                        }
                        validityPeriod {
                            startTime
                            endTime
                        }
                        affects {
                            __typename
                            ... on AffectedLine {
                                line {
                                    publicCode
                                    name
                                    transportMode
                                }
                            }
                            ... on AffectedStopPlace {
                                stopPlace {
                                    id
                                    name
                                }
                            }
                            ... on AffectedStopPlaceOnLine {
                                line {
                                    publicCode
                                    name
                                    transportMode
                                }
                                stopPlace {
                                    id
                                    name
                                }
                            }
                            ... on AffectedServiceJourney {
                                serviceJourney {
                                    line {
                                        publicCode
                                        name
                                        transportMode
                                    }
                                }
                            }
                        }
                    }
                }""";

    private static final String nearestQuery = """
                {
                    nearest(
                        latitude: %f
                        longitude: %f
                        maximumDistance: %.1f
                        maximumResults: %d
                        filterByPlaceTypes: [stopPlace]
                        %s
                    ) {
                        edges {
                            node {
                                distance
                                place {
                                    __typename
                                    ... on StopPlace {
                                        id
                                        name
                                        latitude
                                        longitude
                                        transportMode
                                        estimatedCalls(numberOfDepartures: 5, timeRange: 1800) {
                                            expectedDepartureTime
                                            empiricalDelay {
                                                p50
                                                p90
                                            }
                                            destinationDisplay {
                                                frontText
                                            }
                                            serviceJourney {
                                                line {
                                                    publicCode
                                                    transportMode
                                                    presentation {
                                                        colour
                                                        textColour
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }""";

    public OtpSearchService(
            @Value("${org.entur.otp.url}") String otpURL,
            @Value("${org.entur.mcp.client_name:entur-mcp}") String etClientName,
            @Autowired GeocoderService geocoderService) {
        this.otpURL = otpURL;
        this.etClientName = etClientName;
        this.geocoderService = geocoderService;
        client = HttpClient.newHttpClient();
        log.info("Initializing OtpSearchService with otpURL='{}', etClientName='{}'", otpURL, etClientName);
    }

    /** A day, so an overnight gap or a service that only runs next morning is still found. */
    private static final int NEXT_DEPARTURE_SEARCH_WINDOW_MINUTES = 1440;

    /** Asks only when the next trip leaves; the caller already reported that none exist. */
    private static final String NEXT_DEPARTURE_QUERY = """
            {
                trip(
                    from: {name: "%s", coordinates: {latitude: %f, longitude: %f}}
                    to: {name: "%s", coordinates: {latitude: %f, longitude: %f}}
                    dateTime: "%s"
                    searchWindow: %d
                    numTripPatterns: 1%s
                ) {
                    tripPatterns {
                        expectedStartTime
                    }
                }
            }
            """;

    public Map<String, Object> handleTripRequest(String from, String to, String departureTime,
                                                 String arrivalTime, Integer maxResults,
                                                 List<String> transportModes) {
        // Validate inputs
        InputValidator.validateLocation(from, "from");
        InputValidator.validateLocation(to, "to");
        InputValidator.validateDateTime(departureTime, "departureTime");
        InputValidator.validateDateTime(arrivalTime, "arrivalTime");
        InputValidator.validateConflictingParameters(departureTime, arrivalTime);
        int validatedMaxResults = InputValidator.validateAndNormalizeMaxResults(maxResults, 3);
        List<String> validatedModes = InputValidator.validateTransportModes(transportModes);

        log.info("Planning trip from '{}' to '{}' (maxResults: {})", from, to, validatedMaxResults);

        // First, geocode the from and to locations if they're not coordinates
        Location fromLocation = getLocation(from);
        Location toLocation = getLocation(to);

        // Construct GraphQL query
        String dateTimeParam = "";
        if (departureTime != null && !departureTime.isEmpty()) {
            dateTimeParam = String.format("dateTime: \"%s\"", departureTime);
            log.debug("Using departure time: {}", departureTime);
        } else if (arrivalTime != null && !arrivalTime.isEmpty()) {
            dateTimeParam = String.format("arriveBy: true dateTime: \"%s\"", arrivalTime);
            log.debug("Using arrival time: {}", arrivalTime);
        }

        // Emitted only when filtering, so the unfiltered query stays byte-identical to before.
        // All four fields are required: with a null accessMode/egressMode OTP only considers
        // transit boardable at the exact origin coordinate and returns zero trip patterns.
        String modesParam = "";
        if (validatedModes != null && !validatedModes.isEmpty()) {
            String modeEntries = validatedModes.stream()
                .map(m -> String.format("{transportMode: %s}", m))
                .collect(Collectors.joining(", ", "[", "]"));
            modesParam = String.format(
                "modes: {accessMode: foot, egressMode: foot, directMode: foot, transportModes: %s}",
                modeEntries);
        }

        // Collapsed into a single placeholder so the unfiltered query (both fragments empty)
        // renders the exact same blank-then-numTripPatterns line the template produced before
        // this filter existed - no stray blank line is introduced.
        String optionalParams = Stream.of(dateTimeParam, modesParam)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.joining("\n                        "));

        String query = String.format(
                baseQuery,
                fromLocation.getPlace(), fromLocation.getLatitude(), fromLocation.getLongitude(),
                toLocation.getPlace(), toLocation.getLatitude(), toLocation.getLongitude(),
                optionalParams, validatedMaxResults
        );

        log.debug("Executing GraphQL query for trip from '{}' to '{}'", fromLocation.getPlace(), toLocation.getPlace());

        // Make the GraphQL request
        Map<String, String> reqBody = new HashMap<>();
        reqBody.put("query", query);

        ObjectMapper objectMapper = new ObjectMapper();
        String reqJSON;
        try {
            reqJSON = objectMapper.writeValueAsString(reqBody);
        } catch (Exception e) {
            log.error("Failed to serialize GraphQL request: {}", e.getMessage());
            throw new TripPlanningException("Failed to create trip request", e);
        }

        // Send the request
        HttpResponse<String> response = sendOtpGraphQlRequest(reqJSON);

        // Parse the response
        Map<String, Object> result;
        try {
            result = objectMapper.readValue(response.body(),
                    new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to parse trip planning response: {}", e.getMessage());
            throw new TripPlanningException("Invalid response format from trip planning API", e);
        }

        // Check for GraphQL errors
        if (result.containsKey("errors")) {
            List<?> errors = (List<?>) result.get("errors");
            if (errors != null && !errors.isEmpty()) {
                log.error("GraphQL query returned errors: {}", errors);
                throw new TripPlanningException(String.format("Trip planning query failed: %s", errors));
            }
        }

        Map<String, Object> data = (Map<String, Object>) result.get("data");
        if (data == null) {
            log.error("GraphQL response contained no data");
            throw new TripPlanningException("No trip data returned from API");
        }

        log.info("Successfully planned trip from '{}' to '{}'", fromLocation.getPlace(), toLocation.getPlace());

        // An empty result for a requested time usually means nothing runs then rather than
        // that no route exists, so look ahead for when service resumes. Only worth doing
        // when a time was given: without one the search already started from now.
        if (isEmptyTripResult(data) && departureTime != null && !departureTime.isEmpty()) {
            findNextDeparture(fromLocation, toLocation, departureTime, modesParam)
                .ifPresent(next -> data.put("nextDepartureTime", next));
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private static boolean isEmptyTripResult(Map<String, Object> data) {
        if (!(data.get("trip") instanceof Map<?, ?> trip)) {
            return false;
        }
        Object patterns = ((Map<String, Object>) trip).get("tripPatterns");
        return patterns instanceof List<?> list && list.isEmpty();
    }

    /**
     * Departure time of the first trip on or after {@code departureTime}.
     *
     * <p>OTP's default search window is short and adaptive, so an overnight or weekend gap
     * simply returns nothing. Re-asking with an explicit day-long window finds the far side
     * of the gap. Deliberately a second request rather than a wider window on the first:
     * widening every search would cost time on the overwhelmingly common case that already
     * has results.
     *
     * <p>Best-effort - a failure here must not turn a valid empty result into an error.
     *
     * @return the next departure as an ISO timestamp, or empty if none within the window
     */
    private Optional<String> findNextDeparture(Location fromLocation, Location toLocation,
                                               String departureTime, String modesParam) {
        String query = String.format(NEXT_DEPARTURE_QUERY,
            fromLocation.getPlace(), fromLocation.getLatitude(), fromLocation.getLongitude(),
            toLocation.getPlace(), toLocation.getLatitude(), toLocation.getLongitude(),
            departureTime, NEXT_DEPARTURE_SEARCH_WINDOW_MINUTES,
            modesParam.isEmpty() ? "" : "\n                        " + modesParam);
        try {
            ObjectMapper mapper = new ObjectMapper();
            HttpResponse<String> response =
                sendOtpGraphQlRequest(mapper.writeValueAsString(Map.of("query", query)));
            Map<String, Object> result = mapper.readValue(response.body(), new TypeReference<>() {});

            Map<String, Object> data = (Map<String, Object>) result.get("data");
            if (data == null || !(data.get("trip") instanceof Map<?, ?> trip)) {
                return Optional.empty();
            }
            Object patterns = ((Map<String, Object>) trip).get("tripPatterns");
            if (!(patterns instanceof List<?> list) || list.isEmpty()) {
                return Optional.empty();
            }
            Object first = list.get(0);
            if (!(first instanceof Map<?, ?> pattern)) {
                return Optional.empty();
            }
            Object start = ((Map<String, Object>) pattern).get("expectedStartTime");
            return start instanceof String iso ? Optional.of(iso) : Optional.empty();
        } catch (Exception e) {
            log.warn("Look-ahead for the next departure failed, reporting no trips only: {}",
                e.getMessage());
            return Optional.empty();
        }
    }

    public HttpResponse<String> sendOtpGraphQlRequest(String reqJSON) {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(otpURL))
                .header("Content-Type", "application/json")
                .header(MetricsUtils.ET_CLIENT_NAME_HEADER, etClientName)
                .POST(HttpRequest.BodyPublishers.ofString(reqJSON))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.error("IO error during trip planning request: {}", e.getMessage());
            throw new TripPlanningException("Network error while planning trip", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Trip planning request was interrupted");
            throw new TripPlanningException("Trip planning request was interrupted", e);
        }

        if (response.statusCode() != 200) {
            log.error("GraphQL API returned status {}: {}", response.statusCode(), response.body());
            throw new TripPlanningException(
                String.format("Trip planning API returned status %d", response.statusCode()));
        }
        return response;
    }

    private Location getLocation(String locationName) {
        Location location;
        try {
            location = geocoderService.geocodeIfNeeded(locationName);
            if (location == null) {
                throw new TripPlanningException("Error geocoding location: received null result");
            }
        } catch (Exception e) {
            log.error("Failed to geocode location '{}': {}", locationName, e.getMessage());
            throw new TripPlanningException("Failed to geocode location: " + locationName, e);
        }
        return location;
    }

    public Map<String, Object> handleDepartureBoardRequest(String stopId, Integer numberOfDepartures,
                                                            String startTime, Integer timeRangeMinutes,
                                                            List<String> transportModes) {
        // Validate inputs
        InputValidator.validateLocation(stopId, "stopId");
        int validatedNumDepartures = InputValidator.validateAndNormalizeMaxResults(numberOfDepartures, 5);
        int validatedTimeRange = InputValidator.validateTimeRange(timeRangeMinutes, 60);
        List<String> validatedModes = InputValidator.validateTransportModes(transportModes);

        log.info("Fetching departures for stop '{}' (numDepartures: {}, timeRange: {} min)",
            stopId, validatedNumDepartures, validatedTimeRange);

        // Build optional parameters
        StringBuilder optionalParams = new StringBuilder();
        if (startTime != null && !startTime.isEmpty()) {
            InputValidator.validateDateTime(startTime, "startTime");
            optionalParams.append(String.format("startTime: \"%s\"\n", startTime));
        }
        if (validatedModes != null && !validatedModes.isEmpty()) {
            String modesStr = validatedModes.stream()
                .collect(Collectors.joining(", ", "[", "]"));
            optionalParams.append(String.format("whiteListedModes: %s\n", modesStr));
        }

        // Convert timeRange from minutes to seconds
        int timeRangeSeconds = validatedTimeRange * 60;

        String query = String.format(
                departureBoardQuery,
                stopId,
                validatedNumDepartures, optionalParams, timeRangeSeconds,
                validatedNumDepartures, optionalParams, timeRangeSeconds
        );

        log.debug("Executing departure board query for stop '{}'", stopId);

        // Make the GraphQL request
        Map<String, String> reqBody = new HashMap<>();
        reqBody.put("query", query);

        ObjectMapper objectMapper = new ObjectMapper();
        String reqJSON;
        try {
            reqJSON = objectMapper.writeValueAsString(reqBody);
        } catch (Exception e) {
            log.error("Failed to serialize GraphQL request: {}", e.getMessage());
            throw new TripPlanningException("Failed to create departure board request", e);
        }

        // Send the request
        HttpResponse<String> response = sendOtpGraphQlRequest(reqJSON);

        // Parse the response
        Map<String, Object> result;
        try {
            result = objectMapper.readValue(response.body(), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to parse departure board response: {}", e.getMessage());
            throw new TripPlanningException("Invalid response format from departure board API", e);
        }

        // Check for GraphQL errors
        if (result.containsKey("errors")) {
            List<?> errors = (List<?>) result.get("errors");
            if (errors != null && !errors.isEmpty()) {
                log.error("GraphQL query returned errors: {}", errors);
                throw new TripPlanningException(String.format("Departure board query failed: %s", errors));
            }
        }

        Map<String, Object> data = (Map<String, Object>) result.get("data");
        if (data == null) {
            log.error("GraphQL response contained no data");
            throw new TripPlanningException("No departure data returned from API");
        }

        Map<String, Object> enriched = new HashMap<>(data);
        enriched.put("numberOfDepartures", validatedNumDepartures);
        enriched.put("timeRangeMinutes", validatedTimeRange);
        if (validatedModes != null && !validatedModes.isEmpty()) {
            enriched.put("transportModes", validatedModes);
        }

        log.info("Successfully fetched departures for stop '{}'", stopId);
        return enriched;
    }

    public Map<String, Object> handleSituationsRequest(List<String> severities) {
        log.info("Fetching situations (severities: {})", severities);

        InputValidator.validateSeverities(severities);

        String filterArg = "";
        if (severities != null && !severities.isEmpty()) {
            filterArg = "(severities: " + severities.stream()
                .collect(Collectors.joining(", ", "[", "]")) + ")";
        }

        String query = String.format(situationsQuery, filterArg);

        Map<String, String> reqBody = new HashMap<>();
        reqBody.put("query", query);

        ObjectMapper objectMapper = new ObjectMapper();
        String reqJSON;
        try {
            reqJSON = objectMapper.writeValueAsString(reqBody);
        } catch (Exception e) {
            log.error("Failed to serialize situations request: {}", e.getMessage());
            throw new TripPlanningException("Failed to create situations request", e);
        }

        HttpResponse<String> response = sendOtpGraphQlRequest(reqJSON);

        Map<String, Object> result;
        try {
            result = objectMapper.readValue(response.body(), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to parse situations response: {}", e.getMessage());
            throw new TripPlanningException("Invalid response format from situations API", e);
        }

        if (result.containsKey("errors")) {
            List<?> errors = (List<?>) result.get("errors");
            if (errors != null && !errors.isEmpty()) {
                log.error("Situations query returned errors: {}", errors);
                throw new TripPlanningException(String.format("Situations query failed: %s", errors));
            }
        }

        Map<String, Object> data = (Map<String, Object>) result.get("data");
        if (data == null) {
            log.error("GraphQL response contained no data");
            throw new TripPlanningException("No data returned from situations API");
        }

        List<?> situations = (List<?>) data.getOrDefault("situations", List.of());
        log.info("Successfully fetched {} situations", situations.size());
        return data;
    }

    public Map<String, Object> handleNearbyStopsRequest(double latitude, double longitude,
                                                         int radiusMeters, int maxResults,
                                                         List<String> transportModes) {
        log.info("Fetching nearby stops at ({}, {}), radius={}m, max={}", latitude, longitude, radiusMeters, maxResults);

        List<String> validatedModes = InputValidator.validateTransportModes(transportModes);
        String modeFilter = "";
        if (validatedModes != null && !validatedModes.isEmpty()) {
            String modes = validatedModes.stream()
                .collect(Collectors.joining(", ", "[", "]"));
            modeFilter = "filterByModes: " + modes;
        }

        String query = String.format(nearestQuery, latitude, longitude, (double) radiusMeters, maxResults, modeFilter);

        Map<String, String> reqBody = new HashMap<>();
        reqBody.put("query", query);

        ObjectMapper objectMapper = new ObjectMapper();
        String reqJSON;
        try {
            reqJSON = objectMapper.writeValueAsString(reqBody);
        } catch (Exception e) {
            log.error("Failed to serialize nearest stops request: {}", e.getMessage());
            throw new TripPlanningException("Failed to create nearby stops request", e);
        }

        HttpResponse<String> response = sendOtpGraphQlRequest(reqJSON);

        Map<String, Object> result;
        try {
            result = objectMapper.readValue(response.body(), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to parse nearby stops response: {}", e.getMessage());
            throw new TripPlanningException("Invalid response format from nearby stops API", e);
        }

        if (result.containsKey("errors")) {
            List<?> errors = (List<?>) result.get("errors");
            if (errors != null && !errors.isEmpty()) {
                log.error("Nearest query returned errors: {}", errors);
                throw new TripPlanningException(String.format("Nearby stops query failed: %s", errors));
            }
        }

        Map<String, Object> data = (Map<String, Object>) result.get("data");
        if (data == null) {
            log.error("GraphQL response contained no data");
            throw new TripPlanningException("No data returned from nearby stops API");
        }

        log.info("Successfully fetched nearby stops at ({}, {})", latitude, longitude);
        return data;
    }
}
