package org.entur.mcp.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiGenerationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiSpec_shouldBeGenerated() throws Exception {
        mockMvc.perform(get("/api/openapi"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openapi").exists())
            .andExpect(jsonPath("$.info.title").value("Entur OpenTripPlanner API"))
            .andExpect(jsonPath("$.info.version").value("1.0.0"))
            .andExpect(jsonPath("$.paths").exists());
    }

    @Test
    void openApiSpec_shouldContainTripEndpoint() throws Exception {
        mockMvc.perform(get("/api/openapi"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/trips']").exists())
            .andExpect(jsonPath("$.paths['/api/trips'].get").exists())
            .andExpect(jsonPath("$.paths['/api/trips'].get.summary").value("Plan a multi-modal journey"));
    }

    @Test
    void openApiSpec_shouldContainGeocodeEndpoint() throws Exception {
        mockMvc.perform(get("/api/openapi"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/geocode']").exists())
            .andExpect(jsonPath("$.paths['/api/geocode'].get").exists())
            .andExpect(jsonPath("$.paths['/api/geocode'].get.summary").value("Geocode a location"));
    }

    @Test
    void openApiSpec_shouldContainDeparturesEndpoint() throws Exception {
        mockMvc.perform(get("/api/openapi"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/departures']").exists())
            .andExpect(jsonPath("$.paths['/api/departures'].get").exists())
            .andExpect(jsonPath("$.paths['/api/departures'].get.summary").value("Get real-time departures"));
    }

    @Test
    void openApiSpec_shouldContainErrorResponseSchema() throws Exception {
        mockMvc.perform(get("/api/openapi"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists())
            .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.error").exists())
            .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.message").exists());
    }

    @Test
    void swaggerUi_shouldBeAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void openApiSpec_shouldNotIncludeHealthEndpoints() throws Exception {
        mockMvc.perform(get("/api/openapi"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/readiness']").doesNotExist())
            .andExpect(jsonPath("$.paths['/liveness']").doesNotExist());
    }
}
