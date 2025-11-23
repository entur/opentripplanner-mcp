package org.entur.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.EmbeddedResource;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.entur.mcp.TestFixtures;
import org.entur.mcp.resources.MapUiResources;
import org.entur.mcp.services.GeocoderService;
import org.entur.mcp.services.OtpSearchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UiEnhancedTools Tests")
class UiEnhancedToolsTest {

    private MockWebServer mockOtpServer;
    private MockWebServer mockGeocoderServer;
    private UiEnhancedTools uiEnhancedTools;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        mockOtpServer = new MockWebServer();
        mockOtpServer.start();

        mockGeocoderServer = new MockWebServer();
        mockGeocoderServer.start();

        GeocoderService geocoderService = new GeocoderService(
            mockGeocoderServer.url("/geocoder").toString(),
            "test-client"
        );

        OtpSearchService otpSearchService = new OtpSearchService(
            mockOtpServer.url("/graphql").toString(),
            "test-client",
            geocoderService
        );

        MapUiResources mapUiResources = new MapUiResources();

        uiEnhancedTools = new UiEnhancedTools(otpSearchService, geocoderService, mapUiResources);
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws Exception {
        mockOtpServer.shutdown();
        mockGeocoderServer.shutdown();
    }

    @Test
    @DisplayName("tripWithMap returns embedded resource with HTML")
    void tripWithMap_returnsEmbeddedResource() throws Exception {
        // Setup mock responses
        mockGeocoderServer.enqueue(new MockResponse()
            .setBody(TestFixtures.createGeocoderResponse("Oslo S", 59.911076, 10.748128))
            .addHeader("Content-Type", "application/json"));
        mockGeocoderServer.enqueue(new MockResponse()
            .setBody(TestFixtures.createGeocoderResponse("Nationaltheatret", 59.914, 10.733))
            .addHeader("Content-Type", "application/json"));
        mockOtpServer.enqueue(new MockResponse()
            .setBody(TestFixtures.createOtpTripResponse())
            .addHeader("Content-Type", "application/json"));

        CallToolResult result = uiEnhancedTools.tripWithMap("Oslo S", "Nationaltheatret", null, null, null);

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(EmbeddedResource.class);

        EmbeddedResource embedded = (EmbeddedResource) result.content().get(0);
        TextResourceContents resource = (TextResourceContents) embedded.resource();
        assertThat(resource.uri()).isEqualTo("ui://otp-mcp/trip-map");
        assertThat(resource.mimeType()).isEqualTo("text/html");
        assertThat(resource.text()).contains("<!DOCTYPE html>");
        assertThat(resource.text()).contains("leaflet");
    }

    @Test
    @DisplayName("geocodeWithMap returns embedded resource with HTML")
    void geocodeWithMap_returnsEmbeddedResource() throws Exception {
        mockGeocoderServer.enqueue(new MockResponse()
            .setBody(TestFixtures.createGeocoderResponse("Oslo S", 59.911076, 10.748128))
            .addHeader("Content-Type", "application/json"));

        CallToolResult result = uiEnhancedTools.geocodeWithMap("Oslo S", 5);

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(EmbeddedResource.class);

        EmbeddedResource embedded = (EmbeddedResource) result.content().get(0);
        TextResourceContents resource = (TextResourceContents) embedded.resource();
        assertThat(resource.uri()).isEqualTo("ui://otp-mcp/location-map");
        assertThat(resource.mimeType()).isEqualTo("text/html");
        assertThat(resource.text()).contains("<!DOCTYPE html>");
        assertThat(resource.text()).contains("leaflet");
        assertThat(resource.text()).contains("Oslo S"); // Injected data
    }

    @Test
    @DisplayName("departuresWithBoard returns embedded resource with HTML")
    void departuresWithBoard_returnsEmbeddedResource() throws Exception {
        mockGeocoderServer.enqueue(new MockResponse()
            .setBody(TestFixtures.createGeocoderResponseWithId("Oslo S", "NSR:StopPlace:337", 59.911076, 10.748128))
            .addHeader("Content-Type", "application/json"));
        mockOtpServer.enqueue(new MockResponse()
            .setBody(TestFixtures.createDepartureBoardResponse())
            .addHeader("Content-Type", "application/json"));

        CallToolResult result = uiEnhancedTools.departuresWithBoard("Oslo S", 10, null, null, null);

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(EmbeddedResource.class);

        EmbeddedResource embedded = (EmbeddedResource) result.content().get(0);
        TextResourceContents resource = (TextResourceContents) embedded.resource();
        assertThat(resource.uri()).isEqualTo("ui://otp-mcp/departures-board");
        assertThat(resource.mimeType()).isEqualTo("text/html");
        assertThat(resource.text()).contains("<!DOCTYPE html>");
    }

    @Test
    @DisplayName("tripWithMap returns error for invalid from location")
    void tripWithMap_returnsErrorForInvalidFromLocation() throws Exception {
        CallToolResult result = uiEnhancedTools.tripWithMap("", "Oslo S", null, null, null);

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(TextContent.class);

        TextContent textContent = (TextContent) result.content().get(0);
        JsonNode json = objectMapper.readTree(textContent.text());
        assertThat(json.has("error")).isTrue();
        assertThat(json.get("error").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("geocodeWithMap returns error for empty text")
    void geocodeWithMap_returnsErrorForEmptyText() throws Exception {
        CallToolResult result = uiEnhancedTools.geocodeWithMap("", 5);

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(TextContent.class);

        TextContent textContent = (TextContent) result.content().get(0);
        JsonNode json = objectMapper.readTree(textContent.text());
        assertThat(json.has("error")).isTrue();
        assertThat(json.get("error").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("departuresWithBoard returns error for empty stop")
    void departuresWithBoard_returnsErrorForEmptyStop() throws Exception {
        CallToolResult result = uiEnhancedTools.departuresWithBoard("", null, null, null, null);

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(TextContent.class);

        TextContent textContent = (TextContent) result.content().get(0);
        JsonNode json = objectMapper.readTree(textContent.text());
        assertThat(json.has("error")).isTrue();
        assertThat(json.get("error").asText()).isEqualTo("VALIDATION_ERROR");
    }
}
