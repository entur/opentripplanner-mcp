package org.entur.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.entur.mcp.exception.GeocodingException;
import org.entur.mcp.exception.TripPlanningException;
import org.entur.mcp.exception.ValidationException;
import org.entur.mcp.model.ErrorResponse;
import org.entur.mcp.model.Location;
import org.entur.mcp.validation.InputValidator;
import org.entur.mcp.services.GeocoderService;
import org.entur.mcp.services.OtpSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.MetaProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TripSearchTool {
    private static final Logger log = LoggerFactory.getLogger(TripSearchTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static final List<String> CSP_SCRIPT_DOMAINS = List.of("https://unpkg.com");
    static final List<String> CSP_MAP_DOMAINS = List.of("https://unpkg.com", "https://tile.openstreetmap.org");
    static final List<String> CSP_API_DOMAINS = List.of(
            "https://unpkg.com",
            "https://api.dev.entur.io",
            "https://api.staging.entur.io",
            "https://api.entur.io"
    );
    static final List<String> CSP_API_DOMAINS_WITH_WS = List.of(
            "https://unpkg.com",
            "https://api.dev.entur.io",
            "https://api.staging.entur.io",
            "https://api.entur.io",
            "wss://api.dev.entur.io",
            "wss://api.staging.entur.io",
            "wss://api.entur.io"
    );

    @Value("classpath:/app/departures-board.html")
    private Resource departuresBoardHtml;

    @Value("classpath:/app/trip-map.html")
    private Resource tripMapHtml;

    @Value("classpath:/app/nearby-stops-map.html")
    private Resource nearbyStopsMapHtml;

    private final OtpSearchService otpSearchService;
    private final GeocoderService geocoderService;
    private final String vehiclesWsUrl;

    public static final class DeparturesUiMeta implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return Map.of("ui", Map.of("resourceUri", "ui://departures/departures-board.html"));
        }
    }

    public static final class DeparturesCspMeta implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return Map.of("ui", Map.of("csp", Map.of(
                "resourceDomains", CSP_SCRIPT_DOMAINS,
                "connectDomains", CSP_API_DOMAINS
            )));
        }
    }

    public static final class AppOnlyMeta implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return Map.of("ui", Map.of("visibility", List.of("app")));
        }
    }

    public static final class TripUiMeta implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return Map.of("ui", Map.of("resourceUri", "ui://trip/trip-map.html"));
        }
    }

    public static final class TripCspMeta implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return Map.of("ui", Map.of("csp", Map.of(
                "resourceDomains", CSP_MAP_DOMAINS,
                "connectDomains", CSP_API_DOMAINS_WITH_WS
            )));
        }
    }

    public static final class NearbyStopsUiMeta implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return Map.of("ui", Map.of("resourceUri", "ui://nearby/nearby-stops-map.html"));
        }
    }

    public static final class NearbyStopsCspMeta implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return Map.of("ui", Map.of("csp", Map.of(
                "resourceDomains", CSP_MAP_DOMAINS,
                "connectDomains", CSP_API_DOMAINS
            )));
        }
    }

    public TripSearchTool(
            @Autowired OtpSearchService otpSearchService,
            @Autowired GeocoderService geocoderService,
            @Value("${org.entur.vehicles.ws.url}") String vehiclesWsUrl
    ) {
        this.otpSearchService = otpSearchService;
        this.geocoderService = geocoderService;
        this.vehiclesWsUrl = vehiclesWsUrl;
    }

    @McpTool(
        name = "trip",
        description = """
                Plan multi-leg public transport routes between two locations across Norway and the Nordic region.
                Use when the user needs journey options with departure/arrival times, transfers, occupancy, and trip duration.
                For real-time departures from a specific stop, use the departures tool instead.""",
        metaProvider = TripSearchTool.TripUiMeta.class
    )
    public String trip(
        @McpToolParam(description = "Starting location (address, place name, or coordinates)", required = true) String from,
        @McpToolParam(description = "Destination location (address, place name, or coordinates)", required = true) String to,
        @McpToolParam(description = "Departure time in ISO format (e.g., 2023-05-26T12:00:00)", required = false) String departureTime,
        @McpToolParam(description = "Arrival time in ISO format (e.g., 2023-05-26T14:00:00)", required = false) String arrivalTime,
        @McpToolParam(description = "Maximum number of trip options to return", required = false) Integer maxResults,
        @McpToolParam(description = "Language for the UI. Detect from the conversation: 'nb' for Norwegian Bokmål, 'nn' for Norwegian Nynorsk, 'en' for English.", required = true) String language
    ) {
        try {
            log.debug("Trip tool called with from='{}', to='{}', departureTime='{}', arrivalTime='{}', maxResults={}, language={}",
                from, to, departureTime, arrivalTime, maxResults, language);

            Map<String, Object> response = otpSearchService.handleTripRequest(from, to, departureTime, arrivalTime, maxResults);
            Map<String, Object> wrapped = new HashMap<>(response);
            wrapped.put("query", Map.of(
                "from", from,
                "to", to,
                "departureTime", departureTime != null ? departureTime : "",
                "arrivalTime", arrivalTime != null ? arrivalTime : "",
                "maxResults", maxResults != null ? maxResults : 3
            ));
            wrapped.put("vehiclesWsUrl", vehiclesWsUrl);
            wrapped.put("language", LanguageUtil.normalize(language));
            return objectMapper.writeValueAsString(wrapped);

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
                        line numbers, destinations, platforms, occupancy, and live delay information. For planning routes
                        between two locations, use the trip tool instead.
                        """,
        metaProvider = TripSearchTool.DeparturesUiMeta.class
    )
    public String departures(
        @McpToolParam(
            description = "Stop place name (e.g., 'Oslo S', 'Bergen stasjon') or NSR ID (e.g., NSR:StopPlace:337)",
            required = true
        ) String stop,

        @McpToolParam(
            description = "Number of departures to return (default: 5, max: 50)",
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
        ) List<String> transportModes,

        @McpToolParam(
            description = "Language for the UI. Detect from the conversation: 'nb' for Norwegian Bokmål, 'nn' for Norwegian Nynorsk, 'en' for English.",
            required = true
        ) String language
    ) {
        try {
            log.debug("Departures tool called with stop='{}', numberOfDepartures={}, startTime='{}', timeRangeMinutes={}, transportModes={}, language={}",
                stop, numberOfDepartures, startTime, timeRangeMinutes, transportModes, language);

            // Resolve stop name to NSR ID if needed
            String stopId = geocoderService.resolveStopId(stop);

            Map<String, Object> response = otpSearchService.handleDepartureBoardRequest(
                stopId, numberOfDepartures, startTime, timeRangeMinutes, transportModes);
            Map<String, Object> wrapped = new HashMap<>(response);
            wrapped.put("language", LanguageUtil.normalize(language));
            return objectMapper.writeValueAsString(wrapped);

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

    @McpTool(
        name = "alerts",
        description = """
            Get active service disruptions and alerts for Norwegian public transport.
            Returns situations with severity, affected lines/stops, validity period,
            and descriptions in Norwegian and English.
            Use when the user asks about delays, cancellations, strikes, or service disruptions.
            Severity levels from lowest to highest: noImpact, verySlight, slight, normal, severe, verySevere.
            """
    )
    public String alerts(
        @McpToolParam(
            description = "Filter by severity. Valid values: noImpact, verySlight, slight, normal, severe, verySevere. Default: returns all severities.",
            required = false
        ) List<String> severities
    ) {
        try {
            log.debug("Alerts tool called with severities={}", severities);
            InputValidator.validateSeverities(severities);
            Map<String, Object> response = otpSearchService.handleSituationsRequest(severities);
            return objectMapper.writeValueAsString(response);
        } catch (ValidationException e) {
            log.warn("Validation error in alerts tool: {} - {}", e.getField(), e.getMessage());
            return toErrorJson(ErrorResponse.validationError(e.getField(), e.getMessage()));
        } catch (TripPlanningException e) {
            log.error("Alerts error: {}", e.getMessage());
            return toErrorJson(ErrorResponse.tripPlanningError(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in alerts tool: {}", e.getMessage(), e);
            return toErrorJson(ErrorResponse.genericError("An unexpected error occurred: " + e.getMessage()));
        }
    }

    @McpTool(
        name = "nearby-stops",
        description = """
                Find public transport stops near a location in Norway. Use this whenever
                the user asks about stops, holdeplasser, or transit options near a place,
                address, or area — not just from their current location. This is the
                canonical tool for "what's nearby" transit questions in Norway and should
                be preferred over general map/place search when the question is about
                public transport.
                
                Accepts addresses ("Rådhusgata 5, Oslo"), place names ("Aker brygge"),
                or coordinates. Returns stops sorted by distance with transport modes
                and live next-departures.
                
                Triggers (English): "stops near X", "what stops are around X", "show me
                transit near X", "what can I take from here", "nearest bus/tram/metro to X".
                
                Triggers (Norwegian): "vis stopp/holdeplasser i nærheten av X", "hva
                går fra X", "nærmeste buss/trikk/T-bane til X", "kollektiv i nærheten
                av X".
                
                Use the trip tool instead for routing between two locations, and the
                departures tool when the user already knows which stop they want.
            """,
        metaProvider = TripSearchTool.NearbyStopsUiMeta.class
    )
    public String nearbyStops(
        @McpToolParam(
            description = "Starting location — address, place name, or lat,lng coordinates",
            required = true
        ) String location,
        @McpToolParam(
            description = "Search radius in metres (default: 500, max: 2000)",
            required = false
        ) Integer radiusMeters,
        @McpToolParam(
            description = "Maximum number of stops to return (default: 10, max: 50)",
            required = false
        ) Integer maxResults,
        @McpToolParam(
            description = "Filter by transport modes: bus, rail, tram, metro, water, air",
            required = false
        ) List<String> transportModes,
        @McpToolParam(
            description = "Language for the UI. Detect from the conversation: 'nb' for Norwegian Bokmål, 'nn' for Norwegian Nynorsk, 'en' for English.",
            required = true
        ) String language
    ) {
        try {
            log.debug("nearby-stops tool called: location='{}', radius={}, maxResults={}, modes={}, language={}",
                location, radiusMeters, maxResults, transportModes, language);

            InputValidator.validateLocation(location, "location");
            int validatedRadius = InputValidator.validateRadius(radiusMeters, 500);
            int validatedMax = InputValidator.validateAndNormalizeMaxResults(maxResults, 10);

            Location loc = geocoderService.geocodeIfNeeded(location);

            Map<String, Object> response = otpSearchService.handleNearbyStopsRequest(
                loc.getLatitude(), loc.getLongitude(), validatedRadius, validatedMax, transportModes);

            Map<String, Object> wrapped = new HashMap<>(response);
            wrapped.put("query", Map.of(
                "location", location,
                "latitude", loc.getLatitude(),
                "longitude", loc.getLongitude(),
                "radiusMeters", validatedRadius
            ));
            wrapped.put("language", LanguageUtil.normalize(language));
            return objectMapper.writeValueAsString(wrapped);

        } catch (ValidationException e) {
            log.warn("Validation error in nearby-stops tool: {} - {}", e.getField(), e.getMessage());
            return toErrorJson(ErrorResponse.validationError(e.getField(), e.getMessage()));
        } catch (GeocodingException e) {
            log.warn("Geocoding error in nearby-stops tool: {} - {}", e.getLocation(), e.getMessage());
            return toErrorJson(ErrorResponse.geocodingError(e.getLocation(), e.getMessage()));
        } catch (TripPlanningException e) {
            log.error("Nearby stops error: {}", e.getMessage());
            return toErrorJson(ErrorResponse.tripPlanningError(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in nearby-stops tool: {}", e.getMessage(), e);
            return toErrorJson(ErrorResponse.genericError("An unexpected error occurred: " + e.getMessage()));
        }
    }

    @McpResource(
        name = "Departures Board",
        uri = "ui://departures/departures-board.html",
        mimeType = "text/html;profile=mcp-app",
        metaProvider = TripSearchTool.DeparturesCspMeta.class
    )
    public String getDeparturesBoardResource() throws IOException {
        return departuresBoardHtml.getContentAsString(Charset.defaultCharset());
    }

    @McpTool(
        name = "poll-departures",
        description = "Refreshes departure data for a stop. Called by the departures UI.",
        metaProvider = TripSearchTool.AppOnlyMeta.class
    )
    public String pollDepartures(
        @McpToolParam(description = "NSR stop place ID (e.g. NSR:StopPlace:337)", required = true) String stop,
        @McpToolParam(description = "Number of departures to return", required = false) Integer numberOfDepartures,
        @McpToolParam(description = "Start time in ISO format", required = false) String startTime,
        @McpToolParam(description = "Time range in minutes", required = false) Integer timeRangeMinutes,
        @McpToolParam(description = "Filter by transport modes", required = false) List<String> transportModes,
        @McpToolParam(description = "Language code (en, nb, nn)", required = false) String language
    ) {
        return departures(stop, numberOfDepartures, startTime, timeRangeMinutes, transportModes, language);
    }

    @McpResource(
        name = "Trip Map",
        uri = "ui://trip/trip-map.html",
        mimeType = "text/html;profile=mcp-app",
        metaProvider = TripSearchTool.TripCspMeta.class
    )
    public String getTripMapResource() throws IOException {
        return tripMapHtml.getContentAsString(Charset.defaultCharset());
    }

    @McpResource(
        name = "Nearby Stops Map",
        uri = "ui://nearby/nearby-stops-map.html",
        mimeType = "text/html;profile=mcp-app",
        metaProvider = TripSearchTool.NearbyStopsCspMeta.class
    )
    public String getNearbyStopsMapResource() throws IOException {
        return nearbyStopsMapHtml.getContentAsString(Charset.defaultCharset());
    }

    @McpTool(
        name = "poll-trip",
        description = "Re-plans a trip with updated parameters. Called by the trip map UI.",
        metaProvider = TripSearchTool.AppOnlyMeta.class
    )
    public String pollTrip(
        @McpToolParam(description = "Starting location", required = true) String from,
        @McpToolParam(description = "Destination location", required = true) String to,
        @McpToolParam(description = "Departure time in ISO format", required = false) String departureTime,
        @McpToolParam(description = "Arrival time in ISO format", required = false) String arrivalTime,
        @McpToolParam(description = "Maximum number of trip options", required = false) Integer maxResults,
        @McpToolParam(description = "Language code (en, nb, nn)", required = false) String language
    ) {
        return trip(from, to, departureTime, arrivalTime, maxResults, language);
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
