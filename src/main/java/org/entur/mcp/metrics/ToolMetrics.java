package org.entur.mcp.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.entur.mcp.exception.GeocodingException;
import org.entur.mcp.exception.MobilityException;
import org.entur.mcp.exception.TripPlanningException;
import org.entur.mcp.exception.ValidationException;
import org.entur.mcp.services.GeocoderService;
import org.entur.mcp.validation.InputValidator;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Usage metrics for the MCP tools.
 *
 * <p>Every MCP call arrives as a POST to {@code /mcp}, so the HTTP-level metrics cannot tell one
 * tool from another. These meters can. Every tag value comes from a small fixed set: nothing the
 * user typed (place names, coordinates, stop IDs) ever becomes a tag, which keeps the metrics
 * free of personal data and the number of time series bounded.
 */
@Component
public class ToolMetrics {

    /** Timer per tool call; its count is the number of calls. */
    public static final String TOOL_CALLS = "mcp.tool.calls";
    /** What kind of location each trip endpoint was given. */
    public static final String TRIP_LOCATIONS = "mcp.trip.locations";
    /** How each trip search was shaped. */
    public static final String TRIP_SEARCHES = "mcp.trip.searches";

    public enum Outcome {
        OK, NO_RESULTS, VALIDATION_ERROR, GEOCODING_ERROR, UPSTREAM_ERROR, ERROR;

        String tag() {
            return name().toLowerCase();
        }

        public static Outcome of(Exception e) {
            if (e instanceof ValidationException) return VALIDATION_ERROR;
            if (e instanceof GeocodingException) return GEOCODING_ERROR;
            if (e instanceof TripPlanningException || e instanceof MobilityException) return UPSTREAM_ERROR;
            return ERROR;
        }
    }

    private final MeterRegistry registry;

    public ToolMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Starts timing a call to {@code tool}.
     *
     * @param language the language as the model sent it, before normalizing: an unknown code is
     *                 tagged "other" rather than counted as the "en" it falls back to, and a
     *                 missing one (or a tool without the parameter) "none"
     */
    public Call start(String tool, String language) {
        return new Call(tool, language);
    }

    /**
     * Records the kind of input given for one end of a trip: whether the model passed a stop ID
     * (as the tool description asks it to for ambiguous names), coordinates, or a free-text name.
     */
    public void tripLocation(String field, String input) {
        registry.counter(TRIP_LOCATIONS, "field", field, "kind", locationKind(input)).increment();
    }

    /**
     * Records the shape of a trip search that got an answer from OTP.
     *
     * @param time "departure", "arrival" or "now"
     * @param nextDeparture "found" or "none" when the look-ahead for the next departure ran,
     *                      "not_run" otherwise
     */
    public void tripSearch(String tool, String time, boolean modesFiltered, String nextDeparture) {
        registry.counter(TRIP_SEARCHES,
                "tool", tool,
                "time", time,
                "modes_filtered", String.valueOf(modesFiltered),
                "next_departure", nextDeparture)
            .increment();
    }

    static String locationKind(String input) {
        if (input == null || input.isBlank()) return "empty";
        if (InputValidator.isNsrStopId(input)) return "stop_id";
        if (GeocoderService.isCoordinates(input)) return "coordinates";
        return "name";
    }

    /**
     * The client type the request filter put in the logging context. Absent when the tool runs
     * off the request thread, which then reports "unknown".
     */
    private static String clientType() {
        String type = MDC.get("client_type");
        return type != null ? type : "unknown";
    }

    public final class Call {
        private static final List<String> LANGUAGES = List.of("en", "nb", "nn");

        private final String tool;
        private final String language;
        private final String clientType;
        private final Timer.Sample sample;
        private boolean finished;

        private Call(String tool, String language) {
            this.tool = tool;
            this.language = language == null || language.isBlank() ? "none"
                : LANGUAGES.contains(language) ? language : "other";
            this.clientType = clientType();
            this.sample = Timer.start(registry);
        }

        public void finish(Outcome outcome) {
            if (finished) return;
            finished = true;
            sample.stop(Timer.builder(TOOL_CALLS)
                .description("MCP tool calls by tool and outcome")
                .tag("tool", tool)
                .tag("outcome", outcome.tag())
                .tag("client_type", clientType)
                .tag("language", language)
                .register(registry));
        }

        public void failed(Exception e) {
            finish(Outcome.of(e));
        }
    }
}
