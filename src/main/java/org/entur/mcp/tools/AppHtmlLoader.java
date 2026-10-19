package org.entur.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads MCP App HTML from the classpath and inlines the vendored Entur Linje
 * stylesheets and third-party scripts into it.
 *
 * <p>The apps are handed to the host as a single {@code text/html;profile=mcp-app}
 * string and rendered in a sandboxed iframe, so a relative
 * {@code <link rel="stylesheet">} has no origin to resolve against. Inlining at serve
 * time keeps one copy of each stylesheet in the repository while still shipping every
 * app self-contained, and preserves the no-build-step property of the UI apps.
 *
 * <p>An app opts into a stylesheet by placing its marker in {@code <head>}: every app
 * carries {@value #TOKEN_MARKER}, and the two that render journeys additionally carry
 * {@value #TRAVEL_MARKER}. Markers are substituted in declaration order, so the travel
 * components always land after the tokens they depend on. A marker an app does not
 * carry is simply skipped.
 *
 * <p>Leaflet and the MCP Apps SDK are inlined the same way rather than loaded from a CDN
 * (see {@code scripts/update-vendor-js.py}). Script in the app can call server tools and
 * post into the conversation, so serving it from our own jar keeps a compromised CDN or
 * package release out of every app, and lets the CSP drop the third-party origin.
 */
@Component
public class AppHtmlLoader {

    /** Design tokens. Every app needs these. */
    public static final String TOKEN_MARKER = "<!--ENTUR_TOKENS-->";

    /** TravelHeader / TravelTag / LegBone. Only the journey-rendering apps need these. */
    public static final String TRAVEL_MARKER = "<!--ENTUR_TRAVEL-->";

    /** Leaflet's stylesheet. The map apps need it. */
    public static final String LEAFLET_CSS_MARKER = "<!--LEAFLET_CSS-->";

    /** Leaflet itself, defining the global {@code L}. */
    public static final String LEAFLET_JS_MARKER = "<!--LEAFLET_JS-->";

    /** The MCP Apps SDK, defining the global {@code McpExtApps}. Every app needs it. */
    public static final String EXT_APPS_MARKER = "<!--EXT_APPS-->";

    private static final Logger log = LoggerFactory.getLogger(AppHtmlLoader.class);

    @Value("classpath:/app/entur-tokens.css")
    private Resource tokensCss;

    @Value("classpath:/app/entur-travel.css")
    private Resource travelCss;

    @Value("classpath:/app/vendor/leaflet.css")
    private Resource leafletCss;

    @Value("classpath:/app/vendor/leaflet.js")
    private Resource leafletJs;

    @Value("classpath:/app/vendor/ext-apps.js")
    private Resource extAppsJs;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Returns the app HTML with its declared stylesheets inlined, caching the result.
     *
     * @param html classpath resource holding the app's HTML
     * @return HTML ready to hand to the MCP host
     * @throws IOException if the app HTML or a stylesheet cannot be read
     */
    public String load(Resource html) throws IOException {
        String key = cacheKey(html);
        if (key != null) {
            String cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        String composed = inlineStylesheets(
            html.getContentAsString(StandardCharsets.UTF_8), html.getDescription()
        );
        if (key != null) {
            cache.put(key, composed);
        }
        return composed;
    }

    /**
     * Identity of a resource for caching, or {@code null} for resources that have no
     * stable one (an in-memory resource, say) and so must not be cached.
     */
    private String cacheKey(Resource html) {
        try {
            return html.getURI().toString();
        } catch (IOException e) {
            return null;
        }
    }

    private String inlineStylesheets(String html, String key) throws IOException {
        // Ordered: tokens first, then anything that resolves against them.
        Map<String, Inline> inlines = new LinkedHashMap<>();
        inlines.put(TOKEN_MARKER, new Inline(tokensCss, "<style>", "</style>"));
        inlines.put(TRAVEL_MARKER, new Inline(travelCss, "<style>", "</style>"));
        inlines.put(LEAFLET_CSS_MARKER, new Inline(leafletCss, "<style>", "</style>"));
        inlines.put(LEAFLET_JS_MARKER, new Inline(leafletJs, "<script>", "</script>"));
        // Tagged so scripts/preview-apps.py can swap in its stand-in SDK.
        inlines.put(EXT_APPS_MARKER,
            new Inline(extAppsJs, "<script data-vendor=\"ext-apps\">", "</script>"));

        if (!html.contains(TOKEN_MARKER)) {
            log.warn("App {} has no {} marker; serving without Entur design tokens", key, TOKEN_MARKER);
        }

        String composed = html;
        for (Map.Entry<String, Inline> entry : inlines.entrySet()) {
            String marker = entry.getKey();
            if (!composed.contains(marker)) {
                continue;
            }
            Inline inline = entry.getValue();
            String content = inline.resource().getContentAsString(StandardCharsets.UTF_8);
            // Literal replacement: the CSS and JS contain $ and \ sequences that
            // String.replaceAll would interpret as group references.
            composed = composed.replace(marker, inline.open() + "\n" + content + "\n" + inline.close());
        }
        return composed;
    }

    private record Inline(Resource resource, String open, String close) {}
}
