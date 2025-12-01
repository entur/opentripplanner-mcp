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
import java.util.stream.Collectors;

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
                                }
                                fromEstimatedCall {
                                    occupancyStatus
                                }
                                toPlace {
                                    name
                                }
                                line {
                                    publicCode
                                    name
                                }
                                aimedStartTime
                                expectedStartTime
                                aimedEndTime
                                expectedEndTime
                                situations {
                                    summary {
                                        value
                                    }
                                }
                            }
                        }
                    }
                }""";

    private static final String departureBoardQuery = """
                {
                    stopPlace(id: "%s") {
                        id
                        name
                        estimatedCalls(
                            numberOfDepartures: %d
                            %s
                            timeRange: %d
                        ) {
                            aimedDepartureTime
                            expectedDepartureTime
                            actualDepartureTime
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
                                }
                            }
                            situations {
                                summary {
                                    value
                                }
                            }
                        }
                    }
                }
                """;

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

    public Map<String, Object> handleTripRequest(String from, String to, String departureTime,
                                                 String arrivalTime, Integer maxResults) {
        // Validate inputs
        InputValidator.validateLocation(from, "from");
        InputValidator.validateLocation(to, "to");
        InputValidator.validateDateTime(departureTime, "departureTime");
        InputValidator.validateDateTime(arrivalTime, "arrivalTime");
        InputValidator.validateConflictingParameters(departureTime, arrivalTime);
        int validatedMaxResults = InputValidator.validateAndNormalizeMaxResults(maxResults, 3);

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

        String query = String.format(
                baseQuery,
                fromLocation.getPlace(), fromLocation.getLatitude(), fromLocation.getLongitude(),
                toLocation.getPlace(), toLocation.getLatitude(), toLocation.getLongitude(),
                dateTimeParam, validatedMaxResults
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
        return data;
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
        int validatedNumDepartures = InputValidator.validateAndNormalizeMaxResults(numberOfDepartures, 10);
        int validatedTimeRange = InputValidator.validateTimeRange(timeRangeMinutes, 60);

        log.info("Fetching departures for stop '{}' (numDepartures: {}, timeRange: {} min)",
            stopId, validatedNumDepartures, validatedTimeRange);

        // Build optional parameters
        StringBuilder optionalParams = new StringBuilder();
        if (startTime != null && !startTime.isEmpty()) {
            InputValidator.validateDateTime(startTime, "startTime");
            optionalParams.append(String.format("startTime: \"%s\"\n", startTime));
        }
        if (transportModes != null && !transportModes.isEmpty()) {
            String modesStr = transportModes.stream()
                .map(String::toLowerCase)
                .collect(Collectors.joining(", ", "[", "]"));
            optionalParams.append(String.format("whiteListedModes: %s\n", modesStr));
        }

        // Convert timeRange from minutes to seconds
        int timeRangeSeconds = validatedTimeRange * 60;

        String query = String.format(
                departureBoardQuery,
                stopId,
                validatedNumDepartures,
                optionalParams,
                timeRangeSeconds
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

        log.info("Successfully fetched departures for stop '{}'", stopId);
        return data;
    }
}
