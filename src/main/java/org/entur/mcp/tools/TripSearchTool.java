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
        description = "Plan and explore public-transport journeys across Norway and the Nordic region."
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
