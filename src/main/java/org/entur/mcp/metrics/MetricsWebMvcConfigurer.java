package org.entur.mcp.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration that registers a HandlerInterceptor to add custom
 * metrics tags based on HTTP headers.
 *
 * This interceptor stores header values as request attributes which can then
 * be used by monitoring and logging systems.
 *
 * Uses optional autowiring so it works in @WebMvcTest contexts where
 * MeterRegistry is not available.
 */
@Configuration
public class MetricsWebMvcConfigurer implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(MetricsWebMvcConfigurer.class);

    private final MeterRegistry meterRegistry;

    public MetricsWebMvcConfigurer(@Autowired(required = false) MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("MetricsWebMvcConfigurer initialized with MeterRegistry: {}",
            meterRegistry != null ? meterRegistry.getClass().getSimpleName() : "null");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Only register interceptor if MeterRegistry is available
        if (meterRegistry != null) {
            log.info("Registering MetricsHeadersInterceptor for /api/** and /mcp paths");
            registry.addInterceptor(new MetricsHeadersInterceptor(meterRegistry))
                .addPathPatterns("/api/**", "/mcp");
        } else {
            log.warn("MeterRegistry is null - MetricsHeadersInterceptor will not be registered");
        }
    }

    /**
     * Interceptor that captures HTTP headers and stores them for metrics tagging.
     */
    private static class MetricsHeadersInterceptor implements HandlerInterceptor {

        private final MeterRegistry meterRegistry;

        public MetricsHeadersInterceptor(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        @Override
        public boolean preHandle(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler) {

            // Store header values as request attributes
            String clientName = request.getHeader("ET-Client-Name");
            if (clientName != null && !clientName.isBlank()) {
                request.setAttribute("metrics.client_name", sanitize(clientName));
            } else {
                request.setAttribute("metrics.client_name", "unknown");
            }

            String clientType = detectClientType(request.getHeader("User-Agent"));
            request.setAttribute("metrics.client_type", clientType);

            String apiVersion = request.getHeader("API-Version");
            if (apiVersion != null && !apiVersion.isBlank()) {
                request.setAttribute("metrics.api_version", sanitize(apiVersion));
            } else {
                request.setAttribute("metrics.api_version", "v1");
            }

            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request,
                                   HttpServletResponse response,
                                   Object handler,
                                   Exception ex) {

            // Record custom metrics with tags from request attributes
            String clientName = (String) request.getAttribute("metrics.client_name");
            String clientType = (String) request.getAttribute("metrics.client_type");
            String endpoint = extractEndpoint(request.getRequestURI());

            meterRegistry.counter("http.requests.by.client",
                    "client_name", clientName != null ? clientName : "unknown",
                    "client_type", clientType != null ? clientType : "unknown",
                    "endpoint", endpoint,
                    "status", String.valueOf(response.getStatus()),
                    "method", request.getMethod())
                .increment();
        }

        private String detectClientType(String userAgent) {
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

        private String extractEndpoint(String uri) {
            if (uri == null) return "unknown";

            // REST API endpoints
            if (uri.contains("/api/trips")) return "trips";
            if (uri.contains("/api/geocode")) return "geocode";
            if (uri.contains("/api/departures")) return "departures";
            if (uri.contains("/api/openapi")) return "openapi";

            // MCP endpoint
            if (uri.contains("/mcp")) return "mcp";

            return "other";
        }

        private String sanitize(String value) {
            if (value == null) return "unknown";
            String sanitized = value.length() > 50 ? value.substring(0, 50) : value;
            return sanitized.replaceAll("[^a-zA-Z0-9-_.]", "_").toLowerCase();
        }
    }
}
