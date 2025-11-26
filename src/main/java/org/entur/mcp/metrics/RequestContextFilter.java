package org.entur.mcp.metrics;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter that populates MDC (Mapped Diagnostic Context) with request metadata
 * for structured logging throughout the request lifecycle.
 *
 * This filter runs early in the filter chain (HIGHEST_PRECEDENCE) to ensure
 * MDC values are available to all downstream components including services,
 * exception handlers, and other filters.
 *
 * MDC values populated:
 * - client_name: From ET-Client-Name header
 * - client_type: Detected from User-Agent
 * - endpoint: Simplified endpoint name
 * - request_id: Generated UUID for request correlation
 *
 * All MDC values are automatically cleared after request completion to prevent
 * thread-local memory leaks.
 */
@Component("metricsRequestContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestContextFilter.class);

    private static final String ET_CLIENT_NAME_HEADER = "ET-Client-Name";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String X_REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            // Populate MDC with request context
            populateMDC(httpRequest);

            log.debug("Request started - Method: {}, URI: {}, Client: {}",
                httpRequest.getMethod(),
                httpRequest.getRequestURI(),
                MDC.get("client_name"));

            // Continue filter chain
            chain.doFilter(request, response);

        } finally {
            // Always clear MDC to prevent thread-local memory leaks
            MDC.clear();
        }
    }

    /**
     * Populate MDC with context from HTTP request headers.
     */
    private void populateMDC(HttpServletRequest request) {
        // Client identification
        String clientName = extractClientName(request);
        MDC.put("client_name", clientName);

        // Client type detection
        String clientType = detectClientType(request);
        MDC.put("client_type", clientType);

        // Endpoint
        String endpoint = extractEndpoint(request);
        MDC.put("endpoint", endpoint);

        // Request ID for correlation (use existing or generate)
        String requestId = request.getHeader(X_REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = java.util.UUID.randomUUID().toString();
        }
        MDC.put("request_id", requestId);

        // HTTP method and URI
        MDC.put("http_method", request.getMethod());
        MDC.put("http_uri", request.getRequestURI());
    }

    private String extractClientName(HttpServletRequest request) {
        String clientName = request.getHeader(ET_CLIENT_NAME_HEADER);
        return (clientName != null && !clientName.isBlank()) ? clientName : "unknown";
    }

    private String detectClientType(HttpServletRequest request) {
        String userAgent = request.getHeader(USER_AGENT_HEADER);

        if (userAgent == null || userAgent.isBlank()) {
            return "unknown";
        }

        String lowerUserAgent = userAgent.toLowerCase();

        if (lowerUserAgent.contains("chatgpt")) return "chatgpt";
        if (lowerUserAgent.contains("claude")) return "claude";
        if (lowerUserAgent.contains("mcp")) return "mcp";
        if (lowerUserAgent.contains("curl")) return "curl";
        if (lowerUserAgent.contains("postman")) return "postman";
        if (lowerUserAgent.contains("mozilla") || lowerUserAgent.contains("chrome") ||
            lowerUserAgent.contains("safari")) return "browser";

        return "other";
    }

    private String extractEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (path == null) return "unknown";

        if (path.contains("/api/trips")) return "trips";
        if (path.contains("/api/geocode")) return "geocode";
        if (path.contains("/api/departures")) return "departures";
        if (path.contains("/api/openapi")) return "openapi";
        if (path.contains("/readiness")) return "readiness";
        if (path.contains("/liveness")) return "liveness";
        if (path.contains("/actuator")) return "actuator";

        return "other";
    }
}
