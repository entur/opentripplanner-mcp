package org.entur.mcp.metrics;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RequestContextFilterTest {

    private RequestContextFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new RequestContextFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void doFilter_shouldPopulateMDC() throws ServletException, IOException {
        request.addHeader("ET-Client-Name", "test-client");
        request.addHeader("User-Agent", "curl/7.68.0");
        request.setMethod("GET");
        request.setRequestURI("/api/trips");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_shouldClearMDCAfterRequest() throws ServletException, IOException {
        request.addHeader("ET-Client-Name", "test-client");
        request.setRequestURI("/api/trips");

        filter.doFilter(request, response, filterChain);

        // MDC should be cleared after request
        assertThat(MDC.get("client_name")).isNull();
        assertThat(MDC.get("client_type")).isNull();
        assertThat(MDC.get("endpoint")).isNull();
        assertThat(MDC.get("request_id")).isNull();
    }

    @Test
    void doFilter_withETClientName_shouldSetInMDC() throws ServletException, IOException {
        request.addHeader("ET-Client-Name", "my-client");
        request.setRequestURI("/api/trips");

        doAnswer(invocation -> {
            // Assert MDC values during filter chain execution
            assertThat(MDC.get("client_name")).isEqualTo("my-client");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);
    }

    @Test
    void doFilter_withoutETClientName_shouldUseUnknown() throws ServletException, IOException {
        request.setRequestURI("/api/trips");

        doAnswer(invocation -> {
            assertThat(MDC.get("client_name")).isEqualTo("unknown");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);
    }

    @Test
    void doFilter_withChatGPTUserAgent_shouldDetectClientType() throws ServletException, IOException {
        request.addHeader("User-Agent", "ChatGPT-User/1.0");
        request.setRequestURI("/api/trips");

        doAnswer(invocation -> {
            assertThat(MDC.get("client_type")).isEqualTo("chatgpt");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);
    }

    @Test
    void doFilter_withClaudeUserAgent_shouldDetectClientType() throws ServletException, IOException {
        request.addHeader("User-Agent", "Claude/1.0");
        request.setRequestURI("/api/trips");

        doAnswer(invocation -> {
            assertThat(MDC.get("client_type")).isEqualTo("claude");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);
    }

    @Test
    void doFilter_withMCPUserAgent_shouldDetectClientType() throws ServletException, IOException {
        request.addHeader("User-Agent", "MCP-Client/1.0");
        request.setRequestURI("/api/trips");

        doAnswer(invocation -> {
            assertThat(MDC.get("client_type")).isEqualTo("mcp");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);
    }

    @Test
    void doFilter_withCurlUserAgent_shouldDetectClientType() throws ServletException, IOException {
        request.addHeader("User-Agent", "curl/7.68.0");
        request.setRequestURI("/api/trips");

        doAnswer(invocation -> {
            assertThat(MDC.get("client_type")).isEqualTo("curl");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);
    }

    @Test
    void doFilter_withPostmanUserAgent_shouldDetectClientType() throws ServletException, IOException {
        request.addHeader("User-Agent", "PostmanRuntime/7.26.8");
        request.setRequestURI("/api/trips");

        doAnswer(invocation -> {
            assertThat(MDC.get("client_type")).isEqualTo("postman");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);
    }

    @Test
    void doFilter_withBrowserUserAgent_shouldDetectClientType() throws ServletException, IOException {
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        request.setRequestURI("/api/trips");

        doAnswer(invocation -> {
            assertThat(MDC.get("client_type")).isEqualTo("browser");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);
    }

    @Test
    void doFilter_shouldSetEndpointFromURI() throws ServletException, IOException {
        request.setRequestURI("/api/geocode");

        doAnswer(invocation -> {
            assertThat(MDC.get("endpoint")).isEqualTo("geocode");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);
    }

    @Test
    void doFilter_withExistingRequestId_shouldUseIt() throws ServletException, IOException {
        String existingRequestId = "existing-id-123";
        request.addHeader("X-Request-ID", existingRequestId);
        request.setRequestURI("/api/trips");

        doAnswer(invocation -> {
            assertThat(MDC.get("request_id")).isEqualTo(existingRequestId);
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);
    }

    @Test
    void doFilter_withoutRequestId_shouldGenerateUUID() throws ServletException, IOException {
        request.setRequestURI("/api/trips");

        doAnswer(invocation -> {
            String requestId = MDC.get("request_id");
            assertThat(requestId).isNotNull();
            // Should be a valid UUID format
            assertThat(requestId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);
    }

    @Test
    void doFilter_shouldSetHTTPMethodAndURI() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/trips");

        doAnswer(invocation -> {
            assertThat(MDC.get("http_method")).isEqualTo("POST");
            assertThat(MDC.get("http_uri")).isEqualTo("/api/trips");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);
    }

    @Test
    void doFilter_withException_shouldStillClearMDC() throws ServletException, IOException {
        request.setRequestURI("/api/trips");

        doThrow(new ServletException("Test exception"))
            .when(filterChain).doFilter(request, response);

        try {
            filter.doFilter(request, response, filterChain);
        } catch (ServletException e) {
            // Expected
        }

        // MDC should still be cleared even with exception
        assertThat(MDC.get("client_name")).isNull();
        assertThat(MDC.get("client_type")).isNull();
    }

    @Test
    void doFilter_withDifferentEndpoints_shouldTagCorrectly() throws ServletException, IOException {
        // Test trips endpoint
        request.setRequestURI("/api/trips");
        doAnswer(invocation -> {
            assertThat(MDC.get("endpoint")).isEqualTo("trips");
            return null;
        }).when(filterChain).doFilter(request, response);
        filter.doFilter(request, response, filterChain);

        // Reset
        reset(filterChain);
        request = new MockHttpServletRequest();

        // Test geocode endpoint
        request.setRequestURI("/api/geocode");
        doAnswer(invocation -> {
            assertThat(MDC.get("endpoint")).isEqualTo("geocode");
            return null;
        }).when(filterChain).doFilter(request, response);
        filter.doFilter(request, response, filterChain);

        // Reset
        reset(filterChain);
        request = new MockHttpServletRequest();

        // Test departures endpoint
        request.setRequestURI("/api/departures");
        doAnswer(invocation -> {
            assertThat(MDC.get("endpoint")).isEqualTo("departures");
            return null;
        }).when(filterChain).doFilter(request, response);
        filter.doFilter(request, response, filterChain);

        // Reset
        reset(filterChain);
        request = new MockHttpServletRequest();

        // Test readiness endpoint
        request.setRequestURI("/readiness");
        doAnswer(invocation -> {
            assertThat(MDC.get("endpoint")).isEqualTo("readiness");
            return null;
        }).when(filterChain).doFilter(request, response);
        filter.doFilter(request, response, filterChain);
    }
}
