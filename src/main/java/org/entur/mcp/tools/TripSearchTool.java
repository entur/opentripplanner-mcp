package org.entur.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.entur.mcp.exception.GeocodingException;
import org.entur.mcp.exception.TripPlanningException;
import org.entur.mcp.exception.ValidationException;
import org.entur.mcp.model.ErrorResponse;
import org.entur.mcp.services.GeocoderService;
import org.entur.mcp.services.OtpSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TripSearchTool {
    private static final Logger log = LoggerFactory.getLogger(TripSearchTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final OtpSearchService otpSearchService;
    private final GeocoderService geocoderService;

    public TripSearchTool(
            @Autowired OtpSearchService otpSearchService,
            @Autowired GeocoderService geocoderService
    ) {
        this.otpSearchService = otpSearchService;
        this.geocoderService = geocoderService;
    }

    @McpTool(
        name = "trip",
        description = """
                Plan multi-leg public transport routes between two locations across Norway and the Nordic region. 
                Use when the user needs journey options with departure/arrival times, transfers, and trip duration. 
                For real-time departures from a specific stop, use the departures tool instead."""
    )
    public String trip(
        @McpToolParam(description = "Starting location (address, place name, or coordinates)", required = true) String from,
        @McpToolParam(description = "Destination location (address, place name, or coordinates)", required = true) String to,
        @McpToolParam(description = "Departure time in ISO format (e.g., 2023-05-26T12:00:00)", required = false) String departureTime,
        @McpToolParam(description = "Arrival time in ISO format (e.g., 2023-05-26T14:00:00)", required = false) String arrivalTime,
        @McpToolParam(description = "Maximum number of trip options to return", required = false) Integer maxResults
    ) {
        try {
            log.debug("Trip tool called with from='{}', to='{}', departureTime='{}', arrivalTime='{}', maxResults={}",
                from, to, departureTime, arrivalTime, maxResults);

            Map<String, Object> response = otpSearchService.handleTripRequest(from, to, departureTime, arrivalTime, maxResults);
            return objectMapper.writeValueAsString(response);

        } catch (ValidationException e) {
            log.warn("Validation error in trip tool: {} - {}", e.getField(), e.getMessage());
            return toErrorJson(ErrorResponse.validationError(e.getField(), e.getMessage()));

        } catch (GeocodingException e) {
            log.warn("Geocoding error in trip tool: {} - {}", e.getLocation(), e.getMessage());
            return toErrorJson(ErrorResponse.geocodingError(e.getLocation(), e.getMessage()));

        } catch (TripPlanningException e) {
            log.error("Trip planning error: {}", e.getMessage());
            return toErrorJson(ErrorResponse.tripPlanningError(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error in trip tool: {}", e.getMessage(), e);
            return toErrorJson(ErrorResponse.genericError(
                "An unexpected error occurred: " + e.getMessage()));
        }
    }

    @McpTool(
        name = "geocode",
        description = "Convert place names or addresses into geographic coordinates."
    )
    public String geocode(
        @McpToolParam(description = "Location text to search for", required = true) String text,
        @McpToolParam(description = "Maximum number of results to return", required = false) int maxResults
    ) {
        try {
            log.debug("Geocode tool called with text='{}', maxResults={}", text, maxResults);

            Map<String, Object> response = geocoderService.handleGeocodeRequest(text, maxResults);
            return objectMapper.writeValueAsString(response);

        } catch (ValidationException e) {
            log.warn("Validation error in geocode tool: {} - {}", e.getField(), e.getMessage());
            return toErrorJson(ErrorResponse.validationError(e.getField(), e.getMessage()));

        } catch (GeocodingException e) {
            log.warn("Geocoding error in geocode tool: {} - {}", e.getLocation(), e.getMessage());
            return toErrorJson(ErrorResponse.geocodingError(e.getLocation(), e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error in geocode tool: {}", e.getMessage(), e);
            return toErrorJson(ErrorResponse.genericError(
                "An unexpected error occurred: " + e.getMessage()));
        }
    }

    @McpTool(
        name = "departures",
        description = """
                        Get real-time departures from a single stop or station in Norway. Use when the user 
                        wants to see what's leaving soon from a specific location. Shows upcoming vehicles with 
                        line numbers, destinations, platforms, and live delay information. For planning routes 
                        between two locations, use the trip tool instead.
                        """
    )
    public String departures(
        @McpToolParam(
            description = "Stop place name (e.g., 'Oslo S', 'Bergen stasjon') or NSR ID (e.g., NSR:StopPlace:337)",
            required = true
        ) String stop,

        @McpToolParam(
            description = "Number of departures to return (default: 10, max: 50)",
            required = false
        ) Integer numberOfDepartures,

        @McpToolParam(
            description = "Start time in ISO format (default: now)",
            required = false
        ) String startTime,

        @McpToolParam(
            description = "Time range in minutes to search (default: 60, max: 1440)",
            required = false
        ) Integer timeRangeMinutes,

        @McpToolParam(
            description = "Filter by transport modes (e.g., 'rail', 'bus', 'tram', 'metro', 'water', 'air')",
            required = false
        ) List<String> transportModes
    ) {
        try {
            log.debug("Departures tool called with stop='{}', numberOfDepartures={}, startTime='{}', timeRangeMinutes={}, transportModes={}",
                stop, numberOfDepartures, startTime, timeRangeMinutes, transportModes);

            // Resolve stop name to NSR ID if needed
            String stopId = geocoderService.resolveStopId(stop);

            Map<String, Object> response = otpSearchService.handleDepartureBoardRequest(
                stopId, numberOfDepartures, startTime, timeRangeMinutes, transportModes);
            return objectMapper.writeValueAsString(response);

        } catch (ValidationException e) {
            log.warn("Validation error in departures tool: {} - {}", e.getField(), e.getMessage());
            return toErrorJson(ErrorResponse.validationError(e.getField(), e.getMessage()));

        } catch (GeocodingException e) {
            log.warn("Geocoding error in departures tool: {} - {}", e.getLocation(), e.getMessage());
            return toErrorJson(ErrorResponse.geocodingError(e.getLocation(), e.getMessage()));

        } catch (TripPlanningException e) {
            log.error("Departure board error: {}", e.getMessage());
            return toErrorJson(ErrorResponse.tripPlanningError(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error in departures tool: {}", e.getMessage(), e);
            return toErrorJson(ErrorResponse.genericError(
                "An unexpected error occurred: " + e.getMessage()));
        }
    }

    /**
     * Helper method to convert ErrorResponse to JSON string
     */
    private String toErrorJson(ErrorResponse errorResponse) {
        try {
            return objectMapper.writeValueAsString(errorResponse);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response: {}", e.getMessage());
            // Fallback to a simple JSON string
            return String.format("{\"error\":\"%s\",\"message\":\"%s\"}",
                errorResponse.getError(), errorResponse.getMessage());
        }
    }
}
