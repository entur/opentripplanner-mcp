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
        String clientName = request.getHeader(MetricsUtils.ET_CLIENT_NAME_HEADER);
        MDC.put("client_name", MetricsUtils.getOrDefault(clientName, "unknown"));

        // Client type detection
        MDC.put("client_type", MetricsUtils.detectClientType(request.getHeader(MetricsUtils.USER_AGENT_HEADER)));

        // Endpoint
        MDC.put("endpoint", MetricsUtils.extractEndpoint(request.getRequestURI()));

        // Request ID for correlation (use existing or generate)
        String requestId = request.getHeader(MetricsUtils.X_REQUEST_ID_HEADER);
        MDC.put("request_id", MetricsUtils.isNullOrBlank(requestId)
            ? java.util.UUID.randomUUID().toString()
            : requestId);

        // HTTP method and URI
        MDC.put("http_method", request.getMethod());
        MDC.put("http_uri", request.getRequestURI());
    }
}
