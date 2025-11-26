package org.entur.mcp.metrics;

import java.util.LinkedHashMap;
import java.util.Map;

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

    /**
     * HTTP header name for API version.
     */
    public static final String API_VERSION_HEADER = "API-Version";

    /**
     * Map of User-Agent patterns to client type names.
     * Order matters - first match wins, so more specific patterns should come first.
     */
    private static final Map<String, String> CLIENT_TYPE_PATTERNS = new LinkedHashMap<>();

    /**
     * Map of URI patterns to endpoint names.
     * Order matters - first match wins, so more specific patterns should come first.
     */
    private static final Map<String, String> ENDPOINT_PATTERNS = new LinkedHashMap<>();

    static {
        // AI/MCP clients
        CLIENT_TYPE_PATTERNS.put("chatgpt", "chatgpt");
        CLIENT_TYPE_PATTERNS.put("claude", "claude");
        CLIENT_TYPE_PATTERNS.put("mcp", "mcp");

        // API testing tools
        CLIENT_TYPE_PATTERNS.put("curl", "curl");
        CLIENT_TYPE_PATTERNS.put("postman", "postman");
        CLIENT_TYPE_PATTERNS.put("insomnia", "insomnia");

        // HTTP clients
        CLIENT_TYPE_PATTERNS.put("okhttp", "okhttp");
        CLIENT_TYPE_PATTERNS.put("java", "java-client");
        CLIENT_TYPE_PATTERNS.put("python", "python-client");

        // REST API endpoints (more specific patterns first)
        ENDPOINT_PATTERNS.put("/api/trips", "trips");
        ENDPOINT_PATTERNS.put("/api/geocode", "geocode");
        ENDPOINT_PATTERNS.put("/api/departures", "departures");
        ENDPOINT_PATTERNS.put("/api/openapi", "openapi");

        // MCP endpoint
        ENDPOINT_PATTERNS.put("/mcp", "mcp");

        // Health/actuator endpoints
        ENDPOINT_PATTERNS.put("/readiness", "readiness");
        ENDPOINT_PATTERNS.put("/liveness", "liveness");
        ENDPOINT_PATTERNS.put("/actuator", "actuator");
    }

    private MetricsUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Checks if a string is null or blank (empty or only whitespace).
     *
     * @param value the string to check
     * @return true if the string is null or blank, false otherwise
     */
    public static boolean isNullOrBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Returns the provided value if it's not null or blank, otherwise returns the default value.
     *
     * @param value the value to check
     * @param defaultValue the default value to return if value is null or blank
     * @return the value or defaultValue
     */
    public static String getOrDefault(String value, String defaultValue) {
        return isNullOrBlank(value) ? defaultValue : value;
    }

    /**
     * Detects the client type from the User-Agent header.
     *
     * @param userAgent the User-Agent header value
     * @return the detected client type (chatgpt, claude, mcp, curl, postman, browser, etc.)
     */
    public static String detectClientType(String userAgent) {
        if (isNullOrBlank(userAgent)) {
            return "unknown";
        }

        String lower = userAgent.toLowerCase();

        // Check against known patterns
        for (Map.Entry<String, String> entry : CLIENT_TYPE_PATTERNS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Check for browser signatures (multiple patterns)
        if (lower.contains("mozilla") || lower.contains("chrome") || lower.contains("safari")) {
            return "browser";
        }

        return "other";
    }

    /**
     * Extracts a simplified endpoint name from the request URI.
     *
     * @param uri the request URI
     * @return the simplified endpoint name (trips, geocode, departures, mcp, etc.)
     */
    public static String extractEndpoint(String uri) {
        if (uri == null) {
            return "unknown";
        }

        // Check against known endpoint patterns
        for (Map.Entry<String, String> entry : ENDPOINT_PATTERNS.entrySet()) {
            if (uri.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

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
