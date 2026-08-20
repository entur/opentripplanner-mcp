package org.entur.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.entur.mcp.exception.GeocodingException;
import org.entur.mcp.exception.MobilityException;
import org.entur.mcp.exception.ValidationException;
import org.entur.mcp.model.ErrorResponse;
import org.entur.mcp.model.Location;
import org.entur.mcp.services.GeocoderService;
import org.entur.mcp.services.MobilityService;
import org.entur.mcp.validation.InputValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.MetaProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MobilityTool {

    private static final Logger log = LoggerFactory.getLogger(MobilityTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("classpath:/app/nearby-mobility-map.html")
    private Resource nearbyMobilityMapHtml;

    private final MobilityService mobilityService;
    private final GeocoderService geocoderService;
    private final AppHtmlLoader appHtmlLoader;

    public static final class NearbyMobilityUiMeta implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return McpMetaProviders.uiMeta("ui://mobility/nearby-mobility-map.html");
        }
    }

    public static final class NearbyMobilityCspMeta implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return McpMetaProviders.cspMeta(
                McpMetaProviders.CSP_MAP_DOMAINS,
                McpMetaProviders.CSP_API_DOMAINS
            );
        }
    }

    public MobilityTool(@Autowired MobilityService mobilityService,
                        @Autowired GeocoderService geocoderService,
                        @Autowired AppHtmlLoader appHtmlLoader) {
        this.mobilityService = mobilityService;
        this.geocoderService = geocoderService;
        this.appHtmlLoader = appHtmlLoader;
    }

    @McpTool(
        name = "nearby-mobility",
        description = """
                Find the closest shared-mobility vehicles (e-scooters, city bikes, car-share, mopeds)
                and rental stations near a location in Norway. Use when the user asks about renting a
                scooter, bike, or car nearby ("nærmeste sparkesykkel", "find an electric scooter",
                "are there city bikes near X"). Returns both free-floating vehicles and docking
                stations within a search radius, sorted by distance. For public transport stops,
                use `nearby-stops` instead.
                """,
        metaProvider = MobilityTool.NearbyMobilityUiMeta.class
    )
    public CallToolResult nearbyMobility(
        @McpToolParam(
            description = "Search location — address, place name, or lat,lng coordinates",
            required = true
        ) String location,
        @McpToolParam(
            description = "Search radius in metres (default: 500, max: 2000)",
            required = false
        ) Integer radius,
        @McpToolParam(
            description = "Filter by form factors: BICYCLE, CARGO_BICYCLE, CAR, MOPED, SCOOTER_STANDING, SCOOTER_SEATED, OTHER",
            required = false
        ) List<String> formFactors,
        @McpToolParam(
            description = "Filter by propulsion types: HUMAN, ELECTRIC_ASSIST, ELECTRIC, COMBUSTION, COMBUSTION_DIESEL, HYBRID, PLUG_IN_HYBRID, HYDROGEN_FUEL_CELL",
            required = false
        ) List<String> propulsionTypes,
        @McpToolParam(
            description = "Filter by operator IDs (e.g. 'YVO:Operator:voi')",
            required = false
        ) List<String> operators,
        @McpToolParam(
            description = "Maximum results per category (vehicles, stations). Default 20, max 50.",
            required = false
        ) Integer maxResults,
        @McpToolParam(
            description = "Language for the UI. Detect from the conversation: 'nb' for Norwegian Bokmål, 'nn' for Norwegian Nynorsk, 'en' for English.",
            required = true
        ) String language
    ) {
        try {
            log.debug("nearby-mobility called: location='{}', radius={}, formFactors={}, propulsionTypes={}, operators={}, maxResults={}, language={}",
                location, radius, formFactors, propulsionTypes, operators, maxResults, language);

            InputValidator.validateLocation(location, "location");
            int validatedRadius = InputValidator.validateRadius(radius, 500);
            int validatedMax = InputValidator.validateAndNormalizeMaxResults(maxResults, 20);
            List<String> normalizedFormFactors = InputValidator.validateFormFactors(formFactors);
            List<String> normalizedPropulsion = InputValidator.validatePropulsionTypes(propulsionTypes);

            Location loc = geocoderService.geocodeIfNeeded(location);

            Map<String, Object> response = mobilityService.findNearby(
                loc.getLatitude(), loc.getLongitude(), validatedRadius,
                normalizedFormFactors, normalizedPropulsion, operators, validatedMax);

            Map<String, Object> wrapped = new HashMap<>(response);
            Map<String, Object> query = new HashMap<>();
            query.put("location", location);
            query.put("latitude", loc.getLatitude());
            query.put("longitude", loc.getLongitude());
            query.put("radiusMeters", validatedRadius);
            query.put("maxResults", validatedMax);
            query.put("formFactors", normalizedFormFactors != null ? normalizedFormFactors : List.of());
            query.put("propulsionTypes", normalizedPropulsion != null ? normalizedPropulsion : List.of());
            query.put("operators", operators != null ? operators : List.of());
            wrapped.put("query", query);
            wrapped.put("language", LanguageUtil.normalize(language));

            return UiPayload.split(wrapped, objectMapper,
                "vehicles[].rentalUris",
                "stations[].rentalUris");

        } catch (ValidationException e) {
            log.warn("Validation error in nearby-mobility: {} - {}", e.getField(), e.getMessage());
            return UiPayload.text(toErrorJson(ErrorResponse.validationError(e.getField(), e.getMessage())));
        } catch (GeocodingException e) {
            log.warn("Geocoding error in nearby-mobility: {} - {}", e.getLocation(), e.getMessage());
            return UiPayload.text(toErrorJson(ErrorResponse.geocodingError(e.getLocation(), e.getMessage())));
        } catch (MobilityException e) {
            log.error("Mobility error: {}", e.getMessage());
            return UiPayload.text(toErrorJson(ErrorResponse.mobilityError(e.getMessage())));
        } catch (Exception e) {
            log.error("Unexpected error in nearby-mobility: {}", e.getMessage(), e);
            return UiPayload.text(toErrorJson(ErrorResponse.genericError("An unexpected error occurred: " + e.getMessage())));
        }
    }

    @McpResource(
        name = "Nearby Mobility Map",
        uri = "ui://mobility/nearby-mobility-map.html",
        mimeType = "text/html;profile=mcp-app",
        metaProvider = MobilityTool.NearbyMobilityCspMeta.class
    )
    public String getNearbyMobilityMapResource() throws IOException {
        return appHtmlLoader.load(nearbyMobilityMapHtml);
    }

    private String toErrorJson(ErrorResponse errorResponse) {
        try {
            return objectMapper.writeValueAsString(errorResponse);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response: {}", e.getMessage());
            return String.format("{\"error\":\"%s\",\"message\":\"%s\"}",
                errorResponse.getError(), errorResponse.getMessage());
        }
    }
}
