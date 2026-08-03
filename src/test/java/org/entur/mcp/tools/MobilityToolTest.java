package org.entur.mcp.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.entur.mcp.TestFixtures;
import org.entur.mcp.exception.GeocodingException;
import org.entur.mcp.exception.MobilityException;
import org.entur.mcp.model.Location;
import org.entur.mcp.services.GeocoderService;
import org.entur.mcp.services.MobilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.entur.mcp.TestFixtures.textOf;
import static org.entur.mcp.TestFixtures.uiMetaOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MobilityTool Unit Tests")
class MobilityToolTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private GeocoderService geocoderService;

    @Mock
    private MobilityService mobilityService;

    private MobilityTool tool;

    @BeforeEach
    void setUp() {
        tool = new MobilityTool(mobilityService, geocoderService);
    }

    @Test
    @DisplayName("Happy path: returns JSON with query and language echoed back")
    void nearbyMobility_happyPath() throws Exception {
        when(geocoderService.geocodeIfNeeded("Grünerløkka"))
            .thenReturn(new Location("Grünerløkka", 59.9239, 10.7570));
        when(mobilityService.findNearby(eq(59.9239), eq(10.7570), eq(500),
                eq(null), eq(null), eq(null), eq(20)))
            .thenReturn(Map.of(
                "vehicles", List.of(),
                "stations", List.of()
            ));

        String json = textOf(tool.nearbyMobility("Grünerløkka", null, null, null, null, null, "nb"));

        Map<String, Object> result = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(result).containsKeys("vehicles", "stations", "query", "language");
        assertThat(result.get("language")).isEqualTo("nb");

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) result.get("query");
        assertThat(query.get("location")).isEqualTo("Grünerløkka");
        assertThat(((Number) query.get("latitude")).doubleValue()).isEqualTo(59.9239);
        assertThat(((Number) query.get("longitude")).doubleValue()).isEqualTo(10.7570);
        assertThat(query.get("radiusMeters")).isEqualTo(500);
    }

    @Test
    @DisplayName("Coordinates input does not call geocoder twice")
    void nearbyMobility_coordinatesInput() {
        when(geocoderService.geocodeIfNeeded("59.91,10.75"))
            .thenReturn(new Location("59.91,10.75", 59.91, 10.75));
        when(mobilityService.findNearby(anyDouble(), anyDouble(), anyInt(),
                any(), any(), any(), anyInt()))
            .thenReturn(Map.of("vehicles", List.of(), "stations", List.of()));

        String json = textOf(tool.nearbyMobility("59.91,10.75", null, null, null, null, null, "en"));

        assertThat(json).contains("\"vehicles\"");
    }

    @Test
    @DisplayName("Geocoder error returns geocoding_error JSON")
    void nearbyMobility_geocoderError_returnsErrorJson() throws Exception {
        when(geocoderService.geocodeIfNeeded("nowhere"))
            .thenThrow(new GeocodingException("nowhere", "Location not found"));

        String json = textOf(tool.nearbyMobility("nowhere", null, null, null, null, null, "en"));

        Map<String, Object> result = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(result.get("error")).isEqualTo("GEOCODING_ERROR");
        verify(mobilityService, never()).findNearby(anyDouble(), anyDouble(), anyInt(),
            any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("Mobility service exception returns mobility_error JSON")
    void nearbyMobility_serviceError_returnsErrorJson() throws Exception {
        when(geocoderService.geocodeIfNeeded(anyString()))
            .thenReturn(new Location("Oslo", 59.91, 10.75));
        when(mobilityService.findNearby(anyDouble(), anyDouble(), anyInt(),
                any(), any(), any(), anyInt()))
            .thenThrow(new MobilityException("Upstream down"));

        String json = textOf(tool.nearbyMobility("Oslo", null, null, null, null, null, "en"));

        Map<String, Object> result = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(result.get("error")).isEqualTo("MOBILITY_ERROR");
        assertThat(result.get("message")).isEqualTo("Upstream down");
    }

    @Test
    @DisplayName("Radius over 2000 returns validation_error")
    void nearbyMobility_radiusTooLarge_returnsValidationError() throws Exception {
        String json = textOf(tool.nearbyMobility("Oslo", 5000, null, null, null, null, "en"));

        Map<String, Object> result = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(result.get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("Invalid form factor returns validation_error")
    void nearbyMobility_invalidFormFactor_returnsValidationError() throws Exception {
        String json = textOf(tool.nearbyMobility("Oslo", null, List.of("AIRPLANE"), null, null, null, "en"));

        Map<String, Object> result = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(result.get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("nearby-mobility should keep rental URIs out of the model-facing content")
    void nearbyMobility_movesRentalUrisToMeta() throws Exception {
        when(geocoderService.geocodeIfNeeded(anyString()))
            .thenReturn(TestFixtures.createOsloLocation());
        when(mobilityService.findNearby(anyDouble(), anyDouble(), anyInt(), any(), any(), any(), anyInt()))
            .thenReturn(TestFixtures.createMobilityResponseMapWithRentalUris());

        CallToolResult result = tool.nearbyMobility(
            "Oslo S", 500, null, null, null, 20, "en");

        assertThat(textOf(result)).doesNotContain("rentalUris");
        assertThat(textOf(result)).contains("currentRangeMeters");
        assertThat(uiMetaOf(result)).containsKeys("vehicles[].rentalUris", "stations[].rentalUris");
    }

    @Test
    @DisplayName("Form factors are normalized to uppercase before being passed to service")
    void nearbyMobility_formFactorsNormalized() {
        when(geocoderService.geocodeIfNeeded(anyString()))
            .thenReturn(new Location("Oslo", 59.91, 10.75));
        when(mobilityService.findNearby(anyDouble(), anyDouble(), anyInt(),
                any(), any(), any(), anyInt()))
            .thenReturn(Map.of("vehicles", List.of(), "stations", List.of()));

        tool.nearbyMobility("Oslo", null, List.of("scooter_standing"), null, null, null, "en");

        verify(mobilityService).findNearby(
            eq(59.91), eq(10.75), eq(500),
            eq(List.of("SCOOTER_STANDING")),
            eq(null),
            eq(null),
            eq(20));
    }
}
