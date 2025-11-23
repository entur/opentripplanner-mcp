package org.entur.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.EmbeddedResource;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.entur.mcp.exception.GeocodingException;
import org.entur.mcp.exception.TripPlanningException;
import org.entur.mcp.exception.ValidationException;
import org.entur.mcp.model.ErrorResponse;
import org.entur.mcp.resources.MapUiResources;
import org.entur.mcp.services.GeocoderService;
import org.entur.mcp.services.OtpSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tools that include UI resource references for rich visualization
 * in clients that support the MCP Apps Extension (SEP-1865).
 */
@Component
public class UiEnhancedTools {
    private static final Logger log = LoggerFactory.getLogger(UiEnhancedTools.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final OtpSearchService otpSearchService;
    private final GeocoderService geocoderService;
    private final MapUiResources mapUiResources;

    public UiEnhancedTools(
            @Autowired OtpSearchService otpSearchService,
            @Autowired GeocoderService geocoderService,
            @Autowired MapUiResources mapUiResources
    ) {
        this.otpSearchService = otpSearchService;
        this.geocoderService = geocoderService;
        this.mapUiResources = mapUiResources;
    }

    @McpTool(
        name = "trip_with_map",
        description = "Plan a journey and display the route on an interactive map. " +
                      "Returns trip data with a visual map showing the route with color-coded transport modes. " +
                      "Use this when the user wants to see their journey visually."
    )
    public CallToolResult tripWithMap(
        @McpToolParam(description = "Starting location (address, place name, or coordinates)", required = true) String from,
        @McpToolParam(description = "Destination location (address, place name, or coordinates)", required = true) String to,
        @McpToolParam(description = "Departure time in ISO format (e.g., 2023-05-26T12:00:00)", required = false) String departureTime,
        @McpToolParam(description = "Arrival time in ISO format (e.g., 2023-05-26T14:00:00)", required = false) String arrivalTime,
        @McpToolParam(description = "Maximum number of trip options to return", required = false) Integer maxResults
    ) {
        try {
            log.debug("trip_with_map called with from='{}', to='{}'", from, to);

            Map<String, Object> tripResponse = otpSearchService.handleTripRequest(
                from, to, departureTime, arrivalTime, maxResults);

            // Add from/to location info for UI template header
            Map<String, Object> response = new HashMap<>();
            response.putAll(tripResponse);
            response.put("from", Map.of("place", from));
            response.put("to", Map.of("place", to));

            String jsonData = objectMapper.writeValueAsString(response);

            // Get the HTML template and inject the data
            TextResourceContents uiResource = (TextResourceContents) mapUiResources.getTripMapUi()
                .contents().get(0);
            String htmlWithData = injectTripDataIntoHtml(uiResource.text(), jsonData);

            return new CallToolResult(
                List.of(
                    new EmbeddedResource(
                        null,
                        new TextResourceContents(
                            "ui://otp-mcp/trip-map",
                            "text/html",
                            htmlWithData
                        )
                    )
                ),
                false
            );

        } catch (ValidationException e) {
            log.warn("Validation error in trip_with_map: {} - {}", e.getField(), e.getMessage());
            return errorResult(ErrorResponse.validationError(e.getField(), e.getMessage()));

        } catch (GeocodingException e) {
            log.warn("Geocoding error in trip_with_map: {} - {}", e.getLocation(), e.getMessage());
            return errorResult(ErrorResponse.geocodingError(e.getLocation(), e.getMessage()));

        } catch (TripPlanningException e) {
            log.error("Trip planning error: {}", e.getMessage());
            return errorResult(ErrorResponse.tripPlanningError(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error in trip_with_map: {}", e.getMessage(), e);
            return errorResult(ErrorResponse.genericError(
                "An unexpected error occurred: " + e.getMessage()));
        }
    }

    @McpTool(
        name = "geocode_with_map",
        description = "Search for locations and display them on an interactive map. " +
                      "Returns geocoding results with a visual map showing all matching locations. " +
                      "Use this when the user wants to see location search results visually."
    )
    public CallToolResult geocodeWithMap(
        @McpToolParam(description = "Location text to search for", required = true) String text,
        @McpToolParam(description = "Maximum number of results to return", required = false) int maxResults
    ) {
        try {
            log.debug("geocode_with_map called with text='{}'", text);

            Map<String, Object> geocodeResponse = geocoderService.handleGeocodeRequest(text, maxResults);

            // Add query to response for UI context
            Map<String, Object> response = new HashMap<>();
            response.putAll(geocodeResponse);
            response.put("query", text);

            String jsonData = objectMapper.writeValueAsString(response);

            // Get the HTML template and inject the data
            TextResourceContents uiResource = (TextResourceContents) mapUiResources.getLocationMapUi()
                .contents().get(0);
            String htmlWithData = injectDataIntoHtml(uiResource.text(), jsonData);

            // Return both data and embedded UI resource
            return new CallToolResult(
                List.of(
                    new EmbeddedResource(
                        null, // annotations
                        new TextResourceContents(
                            "ui://otp-mcp/location-map",
                            "text/html",
                            htmlWithData
                        )
                    )
                ),
                false
            );

        } catch (ValidationException e) {
            log.warn("Validation error in geocode_with_map: {} - {}", e.getField(), e.getMessage());
            return errorResult(ErrorResponse.validationError(e.getField(), e.getMessage()));

        } catch (GeocodingException e) {
            log.warn("Geocoding error in geocode_with_map: {} - {}", e.getLocation(), e.getMessage());
            return errorResult(ErrorResponse.geocodingError(e.getLocation(), e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error in geocode_with_map: {}", e.getMessage(), e);
            return errorResult(ErrorResponse.genericError("An unexpected error occurred: " + e.getMessage()));
        }
    }

    private String injectDataIntoHtml(String html, String jsonData) {
        // Inject the data as a script that auto-loads for geocode
        String dataScript = String.format("""
            <script>
                window.mcpData = %s;
                window.addEventListener('DOMContentLoaded', function() {
                    if (typeof loadGeocodeData === 'function') {
                        loadGeocodeData(window.mcpData);
                    }
                });
            </script>
            """, jsonData);
        return html.replace("</body>", dataScript + "</body>");
    }

    private String injectTripDataIntoHtml(String html, String jsonData) {
        // Inject the data as a script that auto-loads for trip
        String dataScript = String.format("""
            <script>
                window.mcpData = %s;
                window.addEventListener('DOMContentLoaded', function() {
                    if (typeof loadTripData === 'function') {
                        loadTripData(window.mcpData);
                    }
                });
            </script>
            """, jsonData);
        return html.replace("</body>", dataScript + "</body>");
    }

    private String injectDeparturesDataIntoHtml(String html, String jsonData) {
        // Inject the data as a script that auto-loads for departures
        String dataScript = String.format("""
            <script>
                window.mcpData = %s;
                window.addEventListener('DOMContentLoaded', function() {
                    if (typeof loadDeparturesData === 'function') {
                        loadDeparturesData(window.mcpData);
                    }
                });
            </script>
            """, jsonData);
        return html.replace("</body>", dataScript + "</body>");
    }

    private CallToolResult errorResult(ErrorResponse errorResponse) {
        return new CallToolResult(
            List.of(new io.modelcontextprotocol.spec.McpSchema.TextContent(
                toErrorJson(errorResponse))),
            true
        );
    }

    @McpTool(
        name = "departures_with_board",
        description = "Get real-time departures displayed as a visual departure board. " +
                      "Returns departure data with a styled departure board visualization. " +
                      "Use this when the user wants to see departures in a clear, visual format."
    )
    public CallToolResult departuresWithBoard(
        @McpToolParam(
            description = "Stop place name (e.g., 'Oslo S', 'Bergen stasjon') or NSR ID",
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
            description = "Filter by transport modes (e.g., 'rail', 'bus', 'tram', 'metro')",
            required = false
        ) List<String> transportModes
    ) {
        try {
            log.debug("departures_with_board called with stop='{}'", stop);

            String stopId = geocoderService.resolveStopId(stop);

            Map<String, Object> departuresResponse = otpSearchService.handleDepartureBoardRequest(
                stopId, numberOfDepartures, startTime, timeRangeMinutes, transportModes);

            // Add stop name to response for UI context
            Map<String, Object> response = new HashMap<>();
            response.putAll(departuresResponse);
            response.put("stopName", stop);

            String jsonData = objectMapper.writeValueAsString(response);

            // Get the HTML template and inject the data
            TextResourceContents uiResource = (TextResourceContents) mapUiResources.getDeparturesBoardUi()
                .contents().get(0);
            String htmlWithData = injectDeparturesDataIntoHtml(uiResource.text(), jsonData);

            return new CallToolResult(
                List.of(
                    new EmbeddedResource(
                        null,
                        new TextResourceContents(
                            "ui://otp-mcp/departures-board",
                            "text/html",
                            htmlWithData
                        )
                    )
                ),
                false
            );

        } catch (ValidationException e) {
            log.warn("Validation error in departures_with_board: {} - {}", e.getField(), e.getMessage());
            return errorResult(ErrorResponse.validationError(e.getField(), e.getMessage()));

        } catch (GeocodingException e) {
            log.warn("Geocoding error in departures_with_board: {} - {}", e.getLocation(), e.getMessage());
            return errorResult(ErrorResponse.geocodingError(e.getLocation(), e.getMessage()));

        } catch (TripPlanningException e) {
            log.error("Departure board error: {}", e.getMessage());
            return errorResult(ErrorResponse.tripPlanningError(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error in departures_with_board: {}", e.getMessage(), e);
            return errorResult(ErrorResponse.genericError(
                "An unexpected error occurred: " + e.getMessage()));
        }
    }

    private String toErrorJson(ErrorResponse errorResponse) {
        try {
            return objectMapper.writeValueAsString(errorResponse);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response: {}", e.getMessage());
            return String.format("{\"error\":\"%s\",\"message\":\"%s\"}",
                errorResponse.getError(), errorResponse.getMessage());
        }
    }
}
