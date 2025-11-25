package org.entur.mcp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openTripPlannerOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Entur OpenTripPlanner API")
                .description("REST API for Norwegian/Nordic public transport trip planning, geocoding, and real-time departures")
                .version("1.0.0")
                .contact(new Contact()
                    .name("Entur")
                    .url("https://entur.no")))
            .servers(List.of(
                new Server()
                    .url("https://api.entur.io/journey-planner-mcp/v1")
                    .description("Production"),
                new Server()
                    .url("https://api.staging.entur.io/journey-planner-mcp/v1")
                    .description("Staging"),
                new Server()
                    .url("https://api.dev.entur.io/journey-planner-mcp/v1")
                    .description("Development"),
                new Server()
                    .url("http://localhost:8080")
                    .description("Local")
            ));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
            .group("public-api")
            .pathsToMatch("/api/**")
            .build();
    }
}
