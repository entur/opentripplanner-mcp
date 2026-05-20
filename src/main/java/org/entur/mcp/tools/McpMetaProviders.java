package org.entur.mcp.tools;

import org.springframework.ai.mcp.annotation.context.MetaProvider;

import java.util.List;
import java.util.Map;

/**
 * Shared MCP _meta.ui builders for tool metadata: resourceUri, csp, visibility.
 * Centralizes the CSP allowlists so every tool stays in sync.
 */
public final class McpMetaProviders {

    public static final List<String> CSP_SCRIPT_DOMAINS = List.of("https://unpkg.com");
    public static final List<String> CSP_MAP_DOMAINS = List.of(
        "https://unpkg.com",
        "https://tile.openstreetmap.org"
    );
    public static final List<String> CSP_API_DOMAINS = List.of(
        "https://unpkg.com",
        "https://api.dev.entur.io",
        "https://api.staging.entur.io",
        "https://api.entur.io"
    );
    public static final List<String> CSP_API_DOMAINS_WITH_WS = List.of(
        "https://unpkg.com",
        "https://api.dev.entur.io",
        "https://api.staging.entur.io",
        "https://api.entur.io",
        "wss://api.dev.entur.io",
        "wss://api.staging.entur.io",
        "wss://api.entur.io"
    );

    private McpMetaProviders() {}

    public static Map<String, Object> uiMeta(String resourceUri) {
        return Map.of("ui", Map.of("resourceUri", resourceUri));
    }

    public static Map<String, Object> cspMeta(List<String> resourceDomains, List<String> connectDomains) {
        return Map.of("ui", Map.of("csp", Map.of(
            "resourceDomains", resourceDomains,
            "connectDomains", connectDomains
        )));
    }

    public static Map<String, Object> appOnlyMeta() {
        return Map.of("ui", Map.of("visibility", List.of("app")));
    }

    public static final class AppOnly implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return appOnlyMeta();
        }
    }
}