package org.entur.mcp.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.entur.mcp.services.GeocoderService;
import org.entur.mcp.services.OtpSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MetricsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoBean
    private OtpSearchService otpSearchService;

    @MockitoBean
    private GeocoderService geocoderService;

    @Test
    void httpRequest_shouldGenerateMetricsWithClientTags() throws Exception {
        // Mock service responses
        Map<String, Object> mockResponse = Map.of("trip", Map.of("tripPatterns", List.of()));
        when(otpSearchService.handleTripRequest(any(), any(), any(), any(), any()))
            .thenReturn(mockResponse);

        // Make request with ET-Client-Name header
        mockMvc.perform(get("/api/trips")
                .param("from", "Oslo S")
                .param("to", "Bergen")
                .header("ET-Client-Name", "test-client")
                .header("User-Agent", "curl/7.68.0"))
            .andExpect(status().isOk());

        // Verify custom metrics were captured with correct tags
        Counter counter = meterRegistry.find("http.requests.by.client")
            .tag("client_name", "test-client")
            .tag("client_type", "curl")
            .tag("endpoint", "trips")
            .tag("status", "200")
            .tag("method", "GET")
            .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isGreaterThan(0);
    }

    @Test
    void httpRequest_withChatGPTUserAgent_shouldTagAsChat() throws Exception {
        Map<String, Object> mockResponse = Map.of("features", List.of());
        when(geocoderService.handleGeocodeRequest(any(), anyInt()))
            .thenReturn(mockResponse);

        mockMvc.perform(get("/api/geocode")
                .param("text", "Oslo")
                .header("ET-Client-Name", "chatgpt-plugin")
                .header("User-Agent", "Mozilla/5.0 ChatGPT"))
            .andExpect(status().isOk());

        Counter counter = meterRegistry.find("http.requests.by.client")
            .tag("client_name", "chatgpt-plugin")
            .tag("client_type", "chatgpt")
            .tag("endpoint", "geocode")
            .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isGreaterThan(0);
    }

    @Test
    void httpRequest_withoutClientHeader_shouldTagAsUnknown() throws Exception {
        Map<String, Object> mockResponse = Map.of("features", List.of());
        when(geocoderService.handleGeocodeRequest(any(), anyInt()))
            .thenReturn(mockResponse);

        mockMvc.perform(get("/api/geocode")
                .param("text", "Oslo"))
            .andExpect(status().isOk());

        Counter counter = meterRegistry.find("http.requests.by.client")
            .tag("client_name", "unknown")
            .tag("client_type", "unknown")
            .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isGreaterThan(0);
    }

    @Test
    void httpRequest_withError_shouldCaptureErrorMetrics() throws Exception {
        mockMvc.perform(get("/api/trips")
                .param("to", "Bergen")
                .header("ET-Client-Name", "error-client"))
            .andExpect(status().isBadRequest());

        Counter counter = meterRegistry.find("http.requests.by.client")
            .tag("client_name", "error-client")
            .tag("status", "400")
            .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isGreaterThan(0);
    }

    @Test
    void httpRequest_toDifferentEndpoints_shouldTagCorrectly() throws Exception {
        Map<String, Object> tripResponse = Map.of("trip", Map.of());
        Map<String, Object> geocodeResponse = Map.of("features", List.of());
        Map<String, Object> departureResponse = Map.of("stopPlace", Map.of());

        when(otpSearchService.handleTripRequest(any(), any(), any(), any(), any()))
            .thenReturn(tripResponse);
        when(geocoderService.handleGeocodeRequest(any(), anyInt()))
            .thenReturn(geocodeResponse);
        when(geocoderService.resolveStopId(any()))
            .thenReturn("NSR:StopPlace:337");
        when(otpSearchService.handleDepartureBoardRequest(any(), any(), any(), any(), any()))
            .thenReturn(departureResponse);

        String clientName = "multi-endpoint-client";

        // Test trips endpoint
        mockMvc.perform(get("/api/trips")
                .param("from", "Oslo")
                .param("to", "Bergen")
                .header("ET-Client-Name", clientName))
            .andExpect(status().isOk());

        // Test geocode endpoint
        mockMvc.perform(get("/api/geocode")
                .param("text", "Oslo")
                .header("ET-Client-Name", clientName))
            .andExpect(status().isOk());

        // Test departures endpoint
        mockMvc.perform(get("/api/departures")
                .param("stop", "Oslo S")
                .header("ET-Client-Name", clientName))
            .andExpect(status().isOk());

        // Verify each endpoint is tagged correctly
        Counter tripsCounter = meterRegistry.find("http.requests.by.client")
            .tag("endpoint", "trips")
            .tag("client_name", clientName)
            .counter();
        assertThat(tripsCounter).isNotNull();
        assertThat(tripsCounter.count()).isGreaterThan(0);

        Counter geocodeCounter = meterRegistry.find("http.requests.by.client")
            .tag("endpoint", "geocode")
            .tag("client_name", clientName)
            .counter();
        assertThat(geocodeCounter).isNotNull();
        assertThat(geocodeCounter.count()).isGreaterThan(0);

        Counter departuresCounter = meterRegistry.find("http.requests.by.client")
            .tag("endpoint", "departures")
            .tag("client_name", clientName)
            .counter();
        assertThat(departuresCounter).isNotNull();
        assertThat(departuresCounter.count()).isGreaterThan(0);
    }

    @Test
    void httpRequest_withClaudeUserAgent_shouldTagCorrectly() throws Exception {
        Map<String, Object> mockResponse = Map.of("features", List.of());
        when(geocoderService.handleGeocodeRequest(any(), anyInt()))
            .thenReturn(mockResponse);

        mockMvc.perform(get("/api/geocode")
                .param("text", "Oslo")
                .header("User-Agent", "Claude/1.0"))
            .andExpect(status().isOk());

        Counter counter = meterRegistry.find("http.requests.by.client")
            .tag("client_type", "claude")
            .counter();

        assertThat(counter).isNotNull();
    }

    @Test
    void httpRequest_withMCPUserAgent_shouldTagCorrectly() throws Exception {
        Map<String, Object> mockResponse = Map.of("features", List.of());
        when(geocoderService.handleGeocodeRequest(any(), anyInt()))
            .thenReturn(mockResponse);

        mockMvc.perform(get("/api/geocode")
                .param("text", "Oslo")
                .header("User-Agent", "MCP-Client/1.0"))
            .andExpect(status().isOk());

        Counter counter = meterRegistry.find("http.requests.by.client")
            .tag("client_type", "mcp")
            .counter();

        assertThat(counter).isNotNull();
    }
}
