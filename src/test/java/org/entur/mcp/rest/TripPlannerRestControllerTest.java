package org.entur.mcp.rest;

import org.entur.mcp.exception.GeocodingException;
import org.entur.mcp.exception.TripPlanningException;
import org.entur.mcp.exception.ValidationException;
import org.entur.mcp.services.GeocoderService;
import org.entur.mcp.services.OtpSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TripPlannerRestController.class)
class TripPlannerRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OtpSearchService otpSearchService;

    @MockitoBean
    private GeocoderService geocoderService;

    // Trip endpoint tests

    @Test
    void planTrip_withValidRequest_shouldReturn200() throws Exception {
        Map<String, Object> mockResponse = Map.of(
            "trip", Map.of("tripPatterns", List.of())
        );
        when(otpSearchService.handleTripRequest(any(), any(), any(), any(), any()))
            .thenReturn(mockResponse);

        mockMvc.perform(get("/api/trips")
                .param("from", "Oslo S")
                .param("to", "Bergen stasjon"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trip").exists());
    }

    @Test
    void planTrip_withMissingFrom_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/trips")
                .param("to", "Bergen stasjon"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void planTrip_withMissingTo_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/trips")
                .param("from", "Oslo S"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void planTrip_withServiceValidationException_shouldReturn400() throws Exception {
        when(otpSearchService.handleTripRequest(any(), any(), any(), any(), any()))
            .thenThrow(new ValidationException("from", "Invalid location"));

        mockMvc.perform(get("/api/trips")
                .param("from", "")
                .param("to", "Bergen"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.field").value("from"));
    }

    @Test
    void planTrip_withGeocodingException_shouldReturn400() throws Exception {
        when(otpSearchService.handleTripRequest(any(), any(), any(), any(), any()))
            .thenThrow(new GeocodingException("Unknown location", "Oslo S"));

        mockMvc.perform(get("/api/trips")
                .param("from", "Oslo S")
                .param("to", "Bergen"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("GEOCODING_ERROR"));
    }

    @Test
    void planTrip_withTripPlanningException_shouldReturn500() throws Exception {
        when(otpSearchService.handleTripRequest(any(), any(), any(), any(), any()))
            .thenThrow(new TripPlanningException("Service unavailable"));

        mockMvc.perform(get("/api/trips")
                .param("from", "Oslo S")
                .param("to", "Bergen"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error").value("TRIP_PLANNING_ERROR"));
    }

    // Geocode endpoint tests

    @Test
    void geocode_withValidRequest_shouldReturn200() throws Exception {
        Map<String, Object> mockResponse = Map.of(
            "features", List.of(
                Map.of("properties", Map.of("name", "Oslo rådhus"))
            )
        );
        when(geocoderService.handleGeocodeRequest(any(), anyInt()))
            .thenReturn(mockResponse);

        mockMvc.perform(get("/api/geocode")
                .param("text", "Oslo rådhus"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.features").exists());
    }

    @Test
    void geocode_withMissingText_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/geocode"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void geocode_withMaxResults_shouldPassToService() throws Exception {
        Map<String, Object> mockResponse = Map.of("features", List.of());
        when(geocoderService.handleGeocodeRequest("Oslo", 5))
            .thenReturn(mockResponse);

        mockMvc.perform(get("/api/geocode")
                .param("text", "Oslo")
                .param("maxResults", "5"))
            .andExpect(status().isOk());
    }

    @Test
    void geocode_withGeocodingException_shouldReturn400() throws Exception {
        when(geocoderService.handleGeocodeRequest(any(), anyInt()))
            .thenThrow(new GeocodingException("No results found", "Unknown place"));

        mockMvc.perform(get("/api/geocode")
                .param("text", "Unknown place"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("GEOCODING_ERROR"));
    }

    // Departures endpoint tests

    @Test
    void getDepartures_withValidRequest_shouldReturn200() throws Exception {
        when(geocoderService.resolveStopId("Oslo S"))
            .thenReturn("NSR:StopPlace:337");

        Map<String, Object> mockResponse = Map.of(
            "stopPlace", Map.of(
                "id", "NSR:StopPlace:337",
                "estimatedCalls", List.of()
            )
        );
        when(otpSearchService.handleDepartureBoardRequest(any(), any(), any(), any(), any()))
            .thenReturn(mockResponse);

        mockMvc.perform(get("/api/departures")
                .param("stop", "Oslo S"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stopPlace").exists());
    }

    @Test
    void getDepartures_withMissingStop_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/departures"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void getDepartures_withAllParameters_shouldPassToService() throws Exception {
        when(geocoderService.resolveStopId("Oslo S"))
            .thenReturn("NSR:StopPlace:337");

        Map<String, Object> mockResponse = Map.of("stopPlace", Map.of());
        when(otpSearchService.handleDepartureBoardRequest(
                eq("NSR:StopPlace:337"),
                eq(20),
                eq("2023-05-26T12:00:00"),
                eq(120),
                eq(List.of("rail", "bus"))))
            .thenReturn(mockResponse);

        mockMvc.perform(get("/api/departures")
                .param("stop", "Oslo S")
                .param("numberOfDepartures", "20")
                .param("startTime", "2023-05-26T12:00:00")
                .param("timeRangeMinutes", "120")
                .param("transportModes", "rail", "bus"))
            .andExpect(status().isOk());
    }

    @Test
    void getDepartures_withStopResolutionFailure_shouldReturn400() throws Exception {
        when(geocoderService.resolveStopId(any()))
            .thenThrow(new GeocodingException("Stop not found", "Unknown stop"));

        mockMvc.perform(get("/api/departures")
                .param("stop", "Unknown stop"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("GEOCODING_ERROR"));
    }
}
