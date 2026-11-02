package org.entur.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.entur.mcp.metrics.ToolMetrics;
import org.entur.mcp.metrics.ToolMetrics.Outcome;
import org.entur.mcp.model.ErrorResponse;
import org.entur.mcp.model.Location;
import org.entur.mcp.validation.InputValidator;
import org.entur.mcp.services.GeocoderService;
import org.entur.mcp.services.OtpSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.MetaProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class TripSearchTool {
    private static final Logger log = LoggerFactory.getLogger(TripSearchTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();




    private final OtpSearchService otpSearchService;
    private final GeocoderService geocoderService;
    private final ToolMetrics toolMetrics;
    private final String vehiclesWsUrl;
    /**
     * ET-Client-Name the trip map sends when it opens the vehicle subscription. The browser
     * makes that connection, so the value is visible to every user: it must never be the
     * server's own {@code org.entur.mcp.client_name}, which is kept secret.
     */
    private final String vehiclesClientName;

    public static final class DeparturesUiMeta implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return McpMetaProviders.uiMeta(AppResources.App.DEPARTURES.uri());
        }
    }


    public static final class AppOnlyMeta implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return McpMetaProviders.appOnlyMeta();
        }
    }

    public static final class TripUiMeta implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return McpMetaProviders.uiMeta(AppResources.App.TRIP.uri());
        }
    }


    public static final class NearbyStopsUiMeta implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return McpMetaProviders.uiMeta(AppResources.App.NEARBY_STOPS.uri());
        }
    }


    public TripSearchTool(
            @Autowired OtpSearchService otpSearchService,
            @Autowired GeocoderService geocoderService,
            @Autowired ToolMetrics toolMetrics,
            @Value("${org.entur.vehicles.ws.url}") String vehiclesWsUrl,
            @Value("${org.entur.vehicles.client_name}") String vehiclesClientName
    ) {
        this.otpSearchService = otpSearchService;
        this.geocoderService = geocoderService;
        this.toolMetrics = toolMetrics;
        this.vehiclesWsUrl = vehiclesWsUrl;
        this.vehiclesClientName = vehiclesClientName;
    }

    @McpTool(
        name = "trip",
        description = """
                Plan multi-leg public transport routes between two locations across Norway and the Nordic region.
                Use when the user needs journey options with departure/arrival times, transfers, occupancy, and trip duration.
                For real-time departures from a specific stop, use the departures tool instead.""",
        metaProvider = TripSearchTool.TripUiMeta.class
    )
    public CallToolResult trip(
        @McpToolParam(description = "Starting location: address, place name, coordinates (lat,lng), or a stop ID (NSR:StopPlace:<digits>). A name uses the geocoder's first match; when it is ambiguous, such as a town with several ferry terminals, call geocode and pass the right stop's ID.", required = true) String from,
        @McpToolParam(description = "Destination location: address, place name, coordinates (lat,lng), or a stop ID (NSR:StopPlace:<digits>). A name uses the geocoder's first match; when it is ambiguous, such as a town with several ferry terminals, call geocode and pass the right stop's ID.", required = true) String to,
        @McpToolParam(description = "Departure time in ISO format (e.g., 2023-05-26T12:00:00)", required = false) String departureTime,
        @McpToolParam(description = "Arrival time in ISO format (e.g., 2023-05-26T14:00:00)", required = false) String arrivalTime,
        @McpToolParam(description = "Maximum number of trip options to return", required = false) Integer maxResults,
        @McpToolParam(description = "Language for the UI. Detect from the conversation: 'nb' for Norwegian Bokmål, 'nn' for Norwegian Nynorsk, 'en' for English.", required = true) String language,
        @McpToolParam(description = "Filter by transport modes: air, bus, cableway, coach, funicular, lift, metro, monorail, rail, taxi, tram, trolleybus, unknown, water. Omit for all modes.", required = false) List<String> transportModes
    ) {
        return planTrip("trip", from, to, departureTime, arrivalTime, maxResults, language, transportModes);
    }

    private CallToolResult planTrip(String tool, String from, String to, String departureTime,
                                    String arrivalTime, Integer maxResults, String language,
                                    List<String> transportModes) {
        ToolMetrics.Call call = toolMetrics.start(tool, language);
        try {
            log.debug("{} called with from='{}', to='{}', departureTime='{}', arrivalTime='{}', maxResults={}, language={}",
                tool, from, to, departureTime, arrivalTime, maxResults, language);

            Map<String, Object> response = otpSearchService.handleTripRequest(
                from, to, departureTime, arrivalTime, maxResults, transportModes);
            recordTripSearch(tool, response, from, to, departureTime, arrivalTime, transportModes);
            Map<String, Object> wrapped = new HashMap<>(response);
            Map<String, Object> query = new HashMap<>();
            query.put("from", from);
            query.put("to", to);
            query.put("departureTime", departureTime != null ? departureTime : "");
            query.put("arrivalTime", arrivalTime != null ? arrivalTime : "");
            query.put("maxResults", maxResults != null ? maxResults : 3);
            query.put("transportModes", transportModes != null ? transportModes : List.of());
            wrapped.put("query", query);
            wrapped.put("vehiclesWsUrl", vehiclesWsUrl);
            wrapped.put("vehiclesClientName", vehiclesClientName);
            wrapped.put("language", LanguageUtil.normalize(language));
            noTripsAdvice(response, departureTime, arrivalTime)
                .ifPresent(advice -> wrapped.put("noTripsFound", advice));
            CallToolResult result = UiPayload.split(wrapped, objectMapper,
                "vehiclesClientName",
                "trip.tripPatterns[].legs[].pointsOnLink",
                "trip.tripPatterns[].legs[].intermediateEstimatedCalls",
                "trip.tripPatterns[].legs[].line.presentation");
            call.finish(isEmptyTripResult(response) ? Outcome.NO_RESULTS : Outcome.OK);
            return result;

        } catch (Exception e) {
            call.failed(e);
            ToolLog.failure(log, tool, e);
            return UiPayload.text(toErrorJson(ErrorResponse.from(e)));
        }
    }

    /**
     * Records the trip meters together, only for searches OTP answered, so that both count the
     * same set of requests and ratios between them hold.
     */
    private void recordTripSearch(String tool, Map<String, Object> response, String from, String to,
                                  String departureTime, String arrivalTime, List<String> transportModes) {
        toolMetrics.tripLocation("from", from);
        toolMetrics.tripLocation("to", to);
        boolean departing = departureTime != null && !departureTime.isEmpty();
        String time = departing ? "departure"
            : arrivalTime != null && !arrivalTime.isEmpty() ? "arrival" : "now";
        // The service looks ahead for the next departure only for an empty, departure-timed search
        String nextDeparture = !(departing && isEmptyTripResult(response)) ? "not_run"
            : response.get("nextDepartureTime") instanceof String ? "found" : "none";
        toolMetrics.tripSearch(tool, time,
            transportModes != null && !transportModes.isEmpty(), nextDeparture);
    }

    @McpTool(
        name = "geocode",
        description = """
                Convert place names or addresses into geographic coordinates.
                Stops come back with an NSR:StopPlace ID in properties.id, which the trip and
                departures tools accept as a location."""
    )
    public String geocode(
        @McpToolParam(description = "Location text to search for", required = true) String text,
        @McpToolParam(description = "Maximum number of results to return", required = false) int maxResults
    ) {
        ToolMetrics.Call call = toolMetrics.start("geocode", null);
        try {
            log.debug("Geocode tool called with text='{}', maxResults={}", text, maxResults);

            Map<String, Object> response = geocoderService.handleGeocodeRequest(text, maxResults);
            String json = objectMapper.writeValueAsString(response);
            call.finish(ToolResults.hasItems(response.get("features")) ? Outcome.OK : Outcome.NO_RESULTS);
            return json;

        } catch (Exception e) {
            call.failed(e);
            ToolLog.failure(log, "geocode", e);
            return toErrorJson(ErrorResponse.from(e));
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
    public CallToolResult departures(
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
            description = "Filter by transport modes: air, bus, cableway, coach, funicular, lift, metro, monorail, rail, taxi, tram, trolleybus, unknown, water. Omit for all modes.",
            required = false
        ) List<String> transportModes,

        @McpToolParam(
            description = "Language for the UI. Detect from the conversation: 'nb' for Norwegian Bokmål, 'nn' for Norwegian Nynorsk, 'en' for English.",
            required = true
        ) String language
    ) {
        return departureBoard("departures", stop, numberOfDepartures, startTime, timeRangeMinutes,
            transportModes, language);
    }

    private CallToolResult departureBoard(String tool, String stop, Integer numberOfDepartures,
                                          String startTime, Integer timeRangeMinutes,
                                          List<String> transportModes, String language) {
        ToolMetrics.Call call = toolMetrics.start(tool, language);
        try {
            log.debug("{} called with stop='{}', numberOfDepartures={}, startTime='{}', timeRangeMinutes={}, transportModes={}, language={}",
                tool, stop, numberOfDepartures, startTime, timeRangeMinutes, transportModes, language);

            // Resolve stop name to NSR ID if needed
            String stopId = geocoderService.resolveStopId(stop);

            Map<String, Object> response = otpSearchService.handleDepartureBoardRequest(
                stopId, numberOfDepartures, startTime, timeRangeMinutes, transportModes);
            Map<String, Object> wrapped = new HashMap<>(response);
            wrapped.put("language", LanguageUtil.normalize(language));
            CallToolResult result = UiPayload.split(wrapped, objectMapper,
                "stopPlace.arrivals[].serviceJourney.line.presentation",
                "stopPlace.departures[].serviceJourney.line.presentation");
            call.finish(hasDepartures(response) ? Outcome.OK : Outcome.NO_RESULTS);
            return result;

        } catch (Exception e) {
            call.failed(e);
            ToolLog.failure(log, tool, e);
            return UiPayload.text(toErrorJson(ErrorResponse.from(e)));
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
        ToolMetrics.Call call = toolMetrics.start("alerts", null);
        try {
            log.debug("Alerts tool called with severities={}", severities);
            InputValidator.validateSeverities(severities);
            Map<String, Object> response = otpSearchService.handleSituationsRequest(severities);
            String json = objectMapper.writeValueAsString(response);
            call.finish(ToolResults.hasItems(response.get("situations")) ? Outcome.OK : Outcome.NO_RESULTS);
            return json;
        } catch (Exception e) {
            call.failed(e);
            ToolLog.failure(log, "alerts", e);
            return toErrorJson(ErrorResponse.from(e));
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
    public CallToolResult nearbyStops(
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
            description = "Filter by transport modes: air, bus, cableway, coach, funicular, lift, metro, monorail, rail, taxi, tram, trolleybus, unknown, water. Omit for all modes.",
            required = false
        ) List<String> transportModes,
        @McpToolParam(
            description = "Language for the UI. Detect from the conversation: 'nb' for Norwegian Bokmål, 'nn' for Norwegian Nynorsk, 'en' for English.",
            required = true
        ) String language
    ) {
        ToolMetrics.Call call = toolMetrics.start("nearby-stops", language);
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
            CallToolResult result = UiPayload.split(wrapped, objectMapper,
                "nearest.edges[].node.place.estimatedCalls[].serviceJourney.line.presentation");
            call.finish(hasNearbyStops(response) ? Outcome.OK : Outcome.NO_RESULTS);
            return result;

        } catch (Exception e) {
            call.failed(e);
            ToolLog.failure(log, "nearby-stops", e);
            return UiPayload.text(toErrorJson(ErrorResponse.from(e)));
        }
    }


    @McpTool(
        name = "poll-departures",
        description = "Refreshes departure data for a stop. Called by the departures UI.",
        metaProvider = TripSearchTool.AppOnlyMeta.class
    )
    public CallToolResult pollDepartures(
        @McpToolParam(description = "NSR stop place ID (e.g. NSR:StopPlace:337)", required = true) String stop,
        @McpToolParam(description = "Number of departures to return", required = false) Integer numberOfDepartures,
        @McpToolParam(description = "Start time in ISO format", required = false) String startTime,
        @McpToolParam(description = "Time range in minutes", required = false) Integer timeRangeMinutes,
        @McpToolParam(description = "Filter by transport modes", required = false) List<String> transportModes,
        @McpToolParam(description = "Language code (en, nb, nn)", required = false) String language
    ) {
        return departureBoard("poll-departures", stop, numberOfDepartures, startTime, timeRangeMinutes,
            transportModes, language);
    }



    @McpTool(
        name = "poll-trip",
        description = "Re-plans a trip with updated parameters. Called by the trip map UI.",
        metaProvider = TripSearchTool.AppOnlyMeta.class
    )
    public CallToolResult pollTrip(
        @McpToolParam(description = "Starting location", required = true) String from,
        @McpToolParam(description = "Destination location", required = true) String to,
        @McpToolParam(description = "Departure time in ISO format", required = false) String departureTime,
        @McpToolParam(description = "Arrival time in ISO format", required = false) String arrivalTime,
        @McpToolParam(description = "Maximum number of trip options", required = false) Integer maxResults,
        @McpToolParam(description = "Language code (en, nb, nn)", required = false) String language,
        @McpToolParam(description = "Filter by transport modes", required = false) List<String> transportModes
    ) {
        return planTrip("poll-trip", from, to, departureTime, arrivalTime, maxResults, language,
            transportModes);
    }

    /** Whether a search OTP answered found no trips; a response without a trip is not one. */
    @SuppressWarnings("unchecked")
    private static boolean isEmptyTripResult(Map<String, Object> response) {
        return response.get("trip") instanceof Map<?, ?> trip
            && !ToolResults.hasItems(((Map<String, Object>) trip).get("tripPatterns"));
    }

    @SuppressWarnings("unchecked")
    private static boolean hasDepartures(Map<String, Object> response) {
        return response.get("stopPlace") instanceof Map<?, ?> stopPlace
            && ToolResults.hasItems(((Map<String, Object>) stopPlace).get("departures"));
    }

    @SuppressWarnings("unchecked")
    private static boolean hasNearbyStops(Map<String, Object> response) {
        return response.get("nearest") instanceof Map<?, ?> nearest
            && ToolResults.hasItems(((Map<String, Object>) nearest).get("edges"));
    }

    /**
     * Advice for the model when a search returns no trips.
     *
     * <p>An empty result is a valid answer, not an error, but on its own it is an empty
     * array with no explanation - so the model cannot tell a badly formed query from a
     * time when nothing runs, and re-issues the same search. Each retry renders another
     * empty UI card. Saying plainly that the query was understood and that repeating it
     * changes nothing is what stops the loop.
     *
     * @return the advice, or empty when the search did in fact return trips
     */
    @SuppressWarnings("unchecked")
    private static Optional<Map<String, Object>> noTripsAdvice(
            Map<String, Object> response, String departureTime, String arrivalTime) {

        Object trip = response.get("trip");
        if (!(trip instanceof Map<?, ?> tripMap)) {
            return Optional.empty();
        }
        Object patterns = ((Map<String, Object>) tripMap).get("tripPatterns");
        if (!(patterns instanceof List<?> list) || !list.isEmpty()) {
            return Optional.empty();
        }

        boolean timed = (departureTime != null && !departureTime.isEmpty())
            || (arrivalTime != null && !arrivalTime.isEmpty());
        Object nextDeparture = response.get("nextDepartureTime");

        String advice;
        if (nextDeparture instanceof String next) {
            advice = "No trips were found for the requested time, but service resumes at "
                + next + ". Repeating this search returns the same result: tell the user "
                + "nothing runs at the time they asked for and give them that next "
                + "departure, or search again with it as the departure time.";
        } else if (timed) {
            advice = "No trips were found for the requested time, and none were found within "
                + "the following 24 hours either. " + locationsAdvice(response)
                + "Repeating this search returns the same result: tell the user, and "
                + "consider whether the locations are served by public transport or whether "
                + "a transport mode filter was too narrow.";
        } else {
            advice = "No trips were found, so no route appears to exist between the locations. "
                + locationsAdvice(response) + "Repeating this "
                + "search returns the same result: tell the user, and consider whether the "
                + "locations are served by public transport or whether a transport mode "
                + "filter was too narrow.";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("retryWillNotHelp", true);
        result.put("advice", advice);
        if (nextDeparture instanceof String next) {
            result.put("nextDepartureTime", next);
        }
        return Optional.of(result);
    }

    /**
     * Names the places the geocoder picked, so the model can check them against what the user
     * meant: a name can resolve to a different stop nearby, and then no amount of retrying the
     * same text helps.
     */
    @SuppressWarnings("unchecked")
    private static String locationsAdvice(Map<String, Object> response) {
        if (!(response.get("resolvedLocations") instanceof Map<?, ?> resolved) || resolved.isEmpty()) {
            return "The locations resolved and the query was valid. ";
        }
        List<String> picks = new ArrayList<>();
        for (String field : List.of("from", "to")) {
            if (resolved.get(field) instanceof Map<?, ?> r) {
                Map<String, Object> place = (Map<String, Object>) r;
                picks.add("'" + field + "' resolved to " + place.get("name")
                    + (place.get("stopId") != null ? " (" + place.get("stopId") + ")" : ""));
            }
        }
        return "The query was valid; " + String.join(" and ", picks) + ". If that is not the "
            + "place the user meant, look it up with geocode and search again with the right "
            + "NSR:StopPlace ID. ";
    }

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
