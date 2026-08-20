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
 * stylesheets into it.
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
 */
@Component
public class AppHtmlLoader {

    /** Design tokens. Every app needs these. */
    public static final String TOKEN_MARKER = "<!--ENTUR_TOKENS-->";

    /** TravelHeader / TravelTag / LegBone. Only the journey-rendering apps need these. */
    public static final String TRAVEL_MARKER = "<!--ENTUR_TRAVEL-->";

    private static final Logger log = LoggerFactory.getLogger(AppHtmlLoader.class);

    @Value("classpath:/app/entur-tokens.css")
    private Resource tokensCss;

    @Value("classpath:/app/entur-travel.css")
    private Resource travelCss;

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
        Map<String, Resource> sheets = new LinkedHashMap<>();
        sheets.put(TOKEN_MARKER, tokensCss);
        sheets.put(TRAVEL_MARKER, travelCss);

        if (!html.contains(TOKEN_MARKER)) {
            log.warn("App {} has no {} marker; serving without Entur design tokens", key, TOKEN_MARKER);
        }

        String composed = html;
        for (Map.Entry<String, Resource> sheet : sheets.entrySet()) {
            String marker = sheet.getKey();
            if (!composed.contains(marker)) {
                continue;
            }
            String css = sheet.getValue().getContentAsString(StandardCharsets.UTF_8);
            // Literal replacement: the CSS contains $ and \ sequences that
            // String.replaceAll would interpret as group references.
            composed = composed.replace(marker, "<style>\n" + css + "\n</style>");
        }
        return composed;
    }
}
