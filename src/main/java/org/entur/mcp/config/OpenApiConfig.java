package org.entur.mcp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                    .url("https://entur.no")));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
            .group("public-api")
            .pathsToMatch("/api/**")
            .build();
    }
}
