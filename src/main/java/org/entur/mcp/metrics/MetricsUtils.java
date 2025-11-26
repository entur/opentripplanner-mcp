package org.entur.mcp.metrics;

/**
 * Utility class for metrics-related operations including client type detection,
 * endpoint extraction, and value sanitization.
 */
public final class MetricsUtils {

    /**
     * HTTP header name for client identification used across Entur services.
     */
    public static final String ET_CLIENT_NAME_HEADER = "ET-Client-Name";

    /**
     * HTTP header name for User-Agent.
     */
    public static final String USER_AGENT_HEADER = "User-Agent";

    /**
     * HTTP header name for request ID correlation.
     */
    public static final String X_REQUEST_ID_HEADER = "X-Request-ID";

    private MetricsUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Detects the client type from the User-Agent header.
     *
     * @param userAgent the User-Agent header value
     * @return the detected client type (chatgpt, claude, mcp, curl, postman, browser, etc.)
     */
    public static String detectClientType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "unknown";
        }

        String lower = userAgent.toLowerCase();

        if (lower.contains("chatgpt")) return "chatgpt";
        if (lower.contains("claude")) return "claude";
        if (lower.contains("mcp")) return "mcp";
        if (lower.contains("curl")) return "curl";
        if (lower.contains("postman")) return "postman";
        if (lower.contains("insomnia")) return "insomnia";
        if (lower.contains("mozilla") || lower.contains("chrome") || lower.contains("safari")) {
            return "browser";
        }
        if (lower.contains("java")) return "java-client";
        if (lower.contains("python")) return "python-client";
        if (lower.contains("okhttp")) return "okhttp";

        return "other";
    }

    /**
     * Extracts a simplified endpoint name from the request URI.
     *
     * @param uri the request URI
     * @return the simplified endpoint name (trips, geocode, departures, mcp, etc.)
     */
    public static String extractEndpoint(String uri) {
        if (uri == null) return "unknown";

        // REST API endpoints
        if (uri.contains("/api/trips")) return "trips";
        if (uri.contains("/api/geocode")) return "geocode";
        if (uri.contains("/api/departures")) return "departures";
        if (uri.contains("/api/openapi")) return "openapi";

        // MCP endpoint
        if (uri.contains("/mcp")) return "mcp";

        // Health/actuator endpoints
        if (uri.contains("/readiness")) return "readiness";
        if (uri.contains("/liveness")) return "liveness";
        if (uri.contains("/actuator")) return "actuator";

        return "other";
    }

    /**
     * Sanitizes a string value for use in metrics tags by limiting length
     * and replacing invalid characters.
     *
     * @param value the value to sanitize
     * @return the sanitized value, or "unknown" if input is null
     */
    public static String sanitize(String value) {
        if (value == null) return "unknown";
        String sanitized = value.length() > 50 ? value.substring(0, 50) : value;
        return sanitized.replaceAll("[^a-zA-Z0-9-_.]", "_").toLowerCase();
    }
}
