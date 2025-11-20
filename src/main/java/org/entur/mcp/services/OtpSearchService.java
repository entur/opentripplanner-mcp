package org.entur.mcp.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.entur.mcp.exception.TripPlanningException;
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

@Service
public class OtpSearchService {

    private static final Logger log = LoggerFactory.getLogger(OtpSearchService.class);

    private final String otpURL;

    private final String etClientName;

    private final GeocoderService geocoderService;

    private final HttpClient client;

    public OtpSearchService(
            @Value("${org.entur.otp.url}") String otpURL,
            @Value("${org.entur.mcp.client_name:entur-mcp}") String etClientName,
            @Autowired GeocoderService geocoderService) {
        this.otpURL = otpURL;
        this.etClientName = etClientName;
        this.geocoderService = geocoderService;
        client = HttpClient.newHttpClient();
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
        Location fromLocation;
        Location toLocation;

        try {
            fromLocation = geocoderService.geocodeIfNeeded(from);
            if (fromLocation == null) {
                throw new TripPlanningException("Error geocoding 'from' location: received null result");
            }
        } catch (Exception e) {
            log.error("Failed to geocode 'from' location '{}': {}", from, e.getMessage());
            throw new TripPlanningException("Failed to geocode 'from' location: " + from, e);
        }

        try {
            toLocation = geocoderService.geocodeIfNeeded(to);
            if (toLocation == null) {
                throw new TripPlanningException("Error geocoding 'to' location: received null result");
            }
        } catch (Exception e) {
            log.error("Failed to geocode 'to' location '{}': {}", to, e.getMessage());
            throw new TripPlanningException("Failed to geocode 'to' location: " + to, e);
        }

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
                """
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
                                    startTime
                                    endTime
                                    legs {
                                        mode
                                        distance
                                        duration
                                        fromPlace {
                                            name
                                        }
                                        toPlace {
                                            name
                                        }
                                        %s
                                    }
                                }
                            }
                        }""",
                fromLocation.getPlace(), fromLocation.getLatitude(), fromLocation.getLongitude(),
                toLocation.getPlace(), toLocation.getLatitude(), toLocation.getLongitude(),
                dateTimeParam, validatedMaxResults, getTransitLegFields()
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(otpURL))
                .header("Content-Type", "application/json")
                .header("ET-Client-Name", etClientName)
                .POST(HttpRequest.BodyPublishers.ofString(reqJSON))
                .build();

        // Send the request
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

    private String getTransitLegFields() {
        // getTransitLegFields returns the GraphQL fields for transit legs
            return "line { " +
                      "publicCode " +
                      "name " +
                    "} " +
                    "aimedStartTime " +
                    "expectedStartTime " +
                    "aimedEndTime " +
                    "expectedEndTime";

    }
}
