package org.entur.mcp.resources;

import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MapUiResources {

    private static final String TRIP_MAP_HTML = loadTemplate("trip-map.html");
    private static final String LOCATION_MAP_HTML = loadTemplate("location-map.html");
    private static final String DEPARTURES_BOARD_HTML = loadTemplate("departures-board.html");

    @McpResource(
        uri = "ui://otp-mcp/trip-map",
        name = "Trip Map Visualization",
        description = "Interactive map displaying journey route with transport mode coloring"
    )
    public ReadResourceResult getTripMapUi() {
        return new ReadResourceResult(List.of(
            new TextResourceContents(
                "ui://otp-mcp/trip-map",
                "text/html+mcp",
                TRIP_MAP_HTML
            )
        ));
    }

    @McpResource(
        uri = "ui://otp-mcp/location-map",
        name = "Location Map",
        description = "Interactive map displaying geocoded locations"
    )
    public ReadResourceResult getLocationMapUi() {
        return new ReadResourceResult(List.of(
            new TextResourceContents(
                "ui://otp-mcp/location-map",
                "text/html+mcp",
                LOCATION_MAP_HTML
            )
        ));
    }

    @McpResource(
        uri = "ui://otp-mcp/departures-board",
        name = "Departures Board",
        description = "Visual departure board with real-time information"
    )
    public ReadResourceResult getDeparturesBoardUi() {
        return new ReadResourceResult(List.of(
            new TextResourceContents(
                "ui://otp-mcp/departures-board",
                "text/html+mcp",
                DEPARTURES_BOARD_HTML
            )
        ));
    }

    private static String loadTemplate(String templateName) {
        try {
            return new String(
                MapUiResources.class.getResourceAsStream("/ui-templates/" + templateName)
                    .readAllBytes()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to load UI template: " + templateName, e);
        }
    }
}
