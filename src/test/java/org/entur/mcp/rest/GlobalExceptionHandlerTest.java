package org.entur.mcp.rest;

import org.entur.mcp.services.GeocoderService;
import org.entur.mcp.services.OtpSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for the catch-all exception handler.
 *
 * <p>{@link GlobalExceptionHandler} declares {@code @ExceptionHandler(Exception.class)} at
 * {@code HIGHEST_PRECEDENCE}. Spring's own MVC exceptions extend {@code Exception}, so without
 * explicit handling the catch-all rewrites every one of them — including ordinary 404s — into a
 * 500. That broke MCP client connections: clients probe {@code .well-known/oauth-*} before
 * connecting, read 404 as "this server needs no auth", and read 500 as a broken server, which
 * sends them into an OAuth flow the server cannot complete.
 */
@WebMvcTest(TripPlannerRestController.class)
@DisplayName("GlobalExceptionHandler Status Code Tests")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OtpSearchService otpSearchService;

    @MockitoBean
    private GeocoderService geocoderService;

    @Test
    @DisplayName("Unmapped path should return 404, not 500")
    void unmappedPath_shouldReturn404() throws Exception {
        mockMvc.perform(get("/no/such/endpoint"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("OAuth protected-resource discovery should 404 so clients treat the server as unauthenticated")
    void oauthProtectedResourceDiscovery_shouldReturn404() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("OAuth protected-resource discovery scoped to /mcp should 404")
    void oauthProtectedResourceForMcp_shouldReturn404() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource/mcp"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("OAuth authorization-server discovery should 404")
    void oauthAuthorizationServerDiscovery_shouldReturn404() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-authorization-server"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Dynamic client registration should 404")
    void dynamicClientRegistration_shouldReturn404() throws Exception {
        mockMvc.perform(post("/register")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Wrong HTTP method on a real endpoint should return 405, not 500")
    void wrongHttpMethod_shouldReturn405() throws Exception {
        mockMvc.perform(post("/api/trips"))
            .andExpect(status().isMethodNotAllowed());
    }
}
