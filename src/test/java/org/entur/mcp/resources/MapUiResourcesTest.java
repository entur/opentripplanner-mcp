package org.entur.mcp.resources;

import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MapUiResources Tests")
class MapUiResourcesTest {

    private MapUiResources mapUiResources;

    @BeforeEach
    void setUp() {
        mapUiResources = new MapUiResources();
    }

    @Test
    @DisplayName("getTripMapUi returns valid HTML resource")
    void getTripMapUi_returnsValidHtmlResource() {
        ReadResourceResult result = mapUiResources.getTripMapUi();

        assertThat(result.contents()).hasSize(1);
        TextResourceContents contents = (TextResourceContents) result.contents().get(0);
        assertThat(contents.uri()).isEqualTo("ui://otp-mcp/trip-map");
        assertThat(contents.mimeType()).isEqualTo("text/html+mcp");
        assertThat(contents.text()).contains("<!DOCTYPE html>");
        assertThat(contents.text()).contains("leaflet");
        assertThat(contents.text()).contains("mcp-ui-ready");
    }

    @Test
    @DisplayName("getLocationMapUi returns valid HTML resource")
    void getLocationMapUi_returnsValidHtmlResource() {
        ReadResourceResult result = mapUiResources.getLocationMapUi();

        assertThat(result.contents()).hasSize(1);
        TextResourceContents contents = (TextResourceContents) result.contents().get(0);
        assertThat(contents.uri()).isEqualTo("ui://otp-mcp/location-map");
        assertThat(contents.mimeType()).isEqualTo("text/html+mcp");
        assertThat(contents.text()).contains("<!DOCTYPE html>");
        assertThat(contents.text()).contains("leaflet");
    }

    @Test
    @DisplayName("getDeparturesBoardUi returns valid HTML resource")
    void getDeparturesBoardUi_returnsValidHtmlResource() {
        ReadResourceResult result = mapUiResources.getDeparturesBoardUi();

        assertThat(result.contents()).hasSize(1);
        TextResourceContents contents = (TextResourceContents) result.contents().get(0);
        assertThat(contents.uri()).isEqualTo("ui://otp-mcp/departures-board");
        assertThat(contents.mimeType()).isEqualTo("text/html+mcp");
        assertThat(contents.text()).contains("<!DOCTYPE html>");
    }

    @Test
    @DisplayName("Trip map template contains map initialization")
    void tripMapTemplate_containsMapInitialization() {
        ReadResourceResult result = mapUiResources.getTripMapUi();
        TextResourceContents contents = (TextResourceContents) result.contents().get(0);

        assertThat(contents.text()).contains("initMap");
        assertThat(contents.text()).contains("L.map");
        assertThat(contents.text()).contains("openstreetmap");
    }

    @Test
    @DisplayName("Location map template contains marker functionality")
    void locationMapTemplate_containsMarkerFunctionality() {
        ReadResourceResult result = mapUiResources.getLocationMapUi();
        TextResourceContents contents = (TextResourceContents) result.contents().get(0);

        assertThat(contents.text()).contains("L.marker");
        assertThat(contents.text()).contains("markersGroup");
    }

    @Test
    @DisplayName("Departures board template contains time formatting")
    void departuresBoardTemplate_containsTimeFormatting() {
        ReadResourceResult result = mapUiResources.getDeparturesBoardUi();
        TextResourceContents contents = (TextResourceContents) result.contents().get(0);

        assertThat(contents.text()).contains("formatTime");
        assertThat(contents.text()).contains("toLocaleTimeString");
    }
}
