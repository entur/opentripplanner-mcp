package org.entur.mcp.tools;

import org.entur.mcp.exception.GeocodingException;
import org.entur.mcp.exception.ValidationException;
import org.entur.mcp.metrics.ToolMetrics.Outcome;
import org.slf4j.Logger;

/**
 * Logs a failed tool call without what the user asked for.
 *
 * <p>Geocoding and trip-planning messages quote the place the user searched for ("No locations
 * found for: ..."), which can be a home address or a position. Those go to the model, which needs
 * them, but only reach the logs at DEBUG, which deployed environments do not enable. Validation
 * messages name the parameter and the accepted values, never the free-text input (keep it that
 * way in {@code InputValidator}), so they stay. Upstream failures (HTTP status, network) are
 * logged at ERROR by the services that saw them. Unexpected exceptions keep their stack trace at
 * ERROR: it is needed to debug them, and they are rare.
 */
final class ToolLog {

    private ToolLog() {
    }

    static void failure(Logger log, String tool, Exception e) {
        switch (Outcome.of(e)) {
            case VALIDATION_ERROR -> {
                ValidationException v = (ValidationException) e;
                log.warn("Validation error in {}: {} - {}", tool, v.getField(), v.getMessage());
            }
            case GEOCODING_ERROR -> {
                log.warn("Geocoding error in {}", tool);
                log.debug("Geocoding error in {}: '{}' - {}", tool,
                    ((GeocodingException) e).getLocation(), e.getMessage());
            }
            case UPSTREAM_ERROR -> {
                log.warn("Upstream error in {}", tool);
                log.debug("Upstream error in {}: {}", tool, e.getMessage());
            }
            default -> log.error("Unexpected error in {}", tool, e);
        }
    }
}
