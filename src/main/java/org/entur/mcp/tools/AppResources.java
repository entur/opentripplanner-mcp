package org.entur.mcp.tools;

import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncResourceTemplateSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * The MCP App UIs, served as resources under URIs that carry a version of their content,
 * e.g. {@code ui://trip/3f2a9c01b7d4/trip-map.html}.
 *
 * <p>Hosts cache an app's HTML by its URI: Claude desktop kept rendering an old trip UI after
 * several deploys, through reconnecting the server and starting new chats. A URI that changes
 * whenever the UI does makes every deploy a cache miss.
 *
 * <p>Each app is registered twice. The exact resource is what {@code resources/list} shows and
 * the tool's {@code _meta.ui.resourceUri} points at. A template matching any version serves
 * the current HTML too, so a conversation from before a deploy still gets a UI when the host
 * asks for the URI it was given then.
 *
 * <p>Registered in code rather than with {@code @McpResource}, whose URI has to be a
 * compile-time constant.
 */
@Configuration
public class AppResources {

    static final String MIME_TYPE = "text/html;profile=mcp-app";

    public enum App {
        DEPARTURES("Departures Board", "departures", "departures-board.html",
            McpMetaProviders.CSP_NO_RESOURCE_DOMAINS, McpMetaProviders.CSP_API_DOMAINS),
        TRIP("Trip Map", "trip", "trip-map.html",
            McpMetaProviders.CSP_MAP_DOMAINS, McpMetaProviders.CSP_API_DOMAINS_WITH_WS),
        NEARBY_STOPS("Nearby Stops Map", "nearby", "nearby-stops-map.html",
            McpMetaProviders.CSP_MAP_DOMAINS, McpMetaProviders.CSP_API_DOMAINS),
        NEARBY_MOBILITY("Nearby Mobility Map", "mobility", "nearby-mobility-map.html",
            McpMetaProviders.CSP_MAP_DOMAINS, McpMetaProviders.CSP_API_DOMAINS);

        private final String title;
        private final String folder;
        private final String file;
        private final List<String> resourceDomains;
        private final List<String> connectDomains;

        App(String title, String folder, String file,
            List<String> resourceDomains, List<String> connectDomains) {
            this.title = title;
            this.folder = folder;
            this.file = file;
            this.resourceDomains = resourceDomains;
            this.connectDomains = connectDomains;
        }

        /** The URI tools point at; changes whenever any app file does. */
        public String uri() {
            return "ui://" + folder + "/" + Version.VALUE + "/" + file;
        }

        String uriTemplate() {
            return "ui://" + folder + "/{version}/" + file;
        }

        Resource html() {
            return new ClassPathResource("app/" + file);
        }

        Map<String, Object> cspMeta() {
            return McpMetaProviders.cspMeta(resourceDomains, connectDomains);
        }
    }

    /**
     * A hash of every file the apps are built from: the HTML, the generated stylesheets and
     * the vendored scripts. One version for all apps keeps it simple; a change to any of them
     * only costs the others one extra fetch.
     */
    static final class Version {
        static final String VALUE = compute();

        private Version() {}

        private static String compute() {
            try {
                Resource[] files = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:app/**/*.*");
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                List<Resource> sorted = Arrays.stream(files)
                    .filter(AppResources::isAppSource)
                    .sorted(Comparator.comparing(AppResources::relativePath))
                    .toList();
                for (Resource file : sorted) {
                    digest.update(relativePath(file).getBytes(StandardCharsets.UTF_8));
                    digest.update(file.getContentAsByteArray());
                }
                return HexFormat.of().formatHex(digest.digest()).substring(0, 12);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read the app files to version them", e);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static boolean isAppSource(Resource file) {
        String name = file.getFilename();
        return name != null && (name.endsWith(".html") || name.endsWith(".css") || name.endsWith(".js"));
    }

    /** The path below {@code app/}, so the hash is the same from a jar and from a directory. */
    private static String relativePath(Resource file) {
        try {
            URL url = file.getURL();
            String path = url.getPath();
            int app = path.lastIndexOf("/app/");
            return app >= 0 ? path.substring(app + "/app/".length()) : path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Bean
    public List<SyncResourceSpecification> appResourceSpecifications(AppHtmlLoader loader) {
        return Arrays.stream(App.values())
            .map(app -> new SyncResourceSpecification(
                McpSchema.Resource.builder()
                    .uri(app.uri())
                    .name(app.title)
                    .description("")
                    .mimeType(MIME_TYPE)
                    .meta(app.cspMeta())
                    .build(),
                (context, request) -> read(loader, app, request.uri())))
            .toList();
    }

    @Bean
    public List<SyncResourceTemplateSpecification> appResourceTemplateSpecifications(AppHtmlLoader loader) {
        return Arrays.stream(App.values())
            .map(app -> new SyncResourceTemplateSpecification(
                McpSchema.ResourceTemplate.builder()
                    .uriTemplate(app.uriTemplate())
                    .name(app.title)
                    .description("")
                    .mimeType(MIME_TYPE)
                    .meta(app.cspMeta())
                    .build(),
                (context, request) -> read(loader, app, request.uri())))
            .toList();
    }

    static McpSchema.ReadResourceResult read(AppHtmlLoader loader, App app, String uri) {
        try {
            return new McpSchema.ReadResourceResult(List.of(new McpSchema.TextResourceContents(
                uri, MIME_TYPE, loader.load(app.html()), app.cspMeta())));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load app " + app.file, e);
        }
    }
}
