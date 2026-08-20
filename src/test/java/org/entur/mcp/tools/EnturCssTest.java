package org.entur.mcp.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
* Guards the vendored Entur Linje stylesheets and the inlining that puts them into
 * each MCP App. These are offline invariants: they catch a bad regeneration
 * (scripts/update-entur-tokens.sh) without needing network access in CI.
 */
class EnturCssTest {

    private static final Pattern DEFINITION = Pattern.compile("(?m)^\\s*(--[a-zA-Z0-9-]+)\\s*:");
    private static final Pattern REFERENCE = Pattern.compile("var\\((--[a-zA-Z0-9-]+)");
    /** An @import at the start of a statement, as opposed to the word inside the file header comment. */
    private static final Pattern REAL_IMPORT = Pattern.compile("(?m)^\\s*@import\\b");

    private static final String[] APPS = {
        "app/departures-board.html",
        "app/trip-map.html",
        "app/nearby-stops-map.html",
        "app/nearby-mobility-map.html",
    };

    /** Apps that render journeys and so opt into the travel components. */
    private static final String[] TRAVEL_APPS = {
        "app/trip-map.html",
        "app/departures-board.html",
    };

    private static String tokensCss() throws IOException {
        return new ClassPathResource("app/entur-tokens.css").getContentAsString(StandardCharsets.UTF_8);
    }

    private static String travelCss() throws IOException {
        return new ClassPathResource("app/entur-travel.css").getContentAsString(StandardCharsets.UTF_8);
    }

    /** A loader wired with every vendored stylesheet, as Spring would inject it. */
    private static AppHtmlLoader loader() {
        AppHtmlLoader loader = new AppHtmlLoader();
        ReflectionTestUtils.setField(loader, "tokensCss", new ClassPathResource("app/entur-tokens.css"));
        ReflectionTestUtils.setField(loader, "travelCss", new ClassPathResource("app/entur-travel.css"));
        return loader;
    }

    @Test
    @DisplayName("every var() reference in the token stylesheet resolves within the same file")
    void tokenReferencesAllResolve() throws IOException {
        String css = tokensCss();

        Set<String> defined = new HashSet<>();
        Matcher d = DEFINITION.matcher(css);
        while (d.find()) {
            defined.add(d.group(1));
        }

        Set<String> unresolved = new TreeSet<>();
        Matcher r = REFERENCE.matcher(css);
        while (r.find()) {
            if (!defined.contains(r.group(1))) {
                unresolved.add(r.group(1));
            }
        }

        assertThat(defined).as("token definitions found").hasSizeGreaterThan(500);
        assertThat(unresolved)
            .as("dangling var() references — re-run scripts/update-entur-tokens.sh")
            .isEmpty();
    }

    @Test
    @DisplayName("no @import survives: browsers cannot resolve the package's bare specifiers")
    void noBareImportsRemain() throws IOException {
        assertThat(REAL_IMPORT.matcher(tokensCss()).find())
            .as("@import statement left in vendored CSS")
            .isFalse();
    }

    @Test
    @DisplayName("dark mode works without JavaScript and honours an explicit light override")
    void darkModeFallbackIsGenerated() throws IOException {
        String css = tokensCss();
        assertThat(css).contains("@media (prefers-color-scheme: dark)");
        assertThat(css).contains(":root:not([data-color-mode='light'])");
        assertThat(css).contains("[data-color-mode='dark']");
        assertThat(css).contains(":root { color-scheme: light dark; }");
    }

    @Test
    @DisplayName("feedback surfaces flip with the colour mode")
    void feedbackFillsAreModeAware() throws IOException {
        String css = tokensCss();

        // Both halves must exist, or a warning/error surface stays light in dark mode.
        assertThat(css).contains("--app-feedback-warning-fill: var(--canary-20)");
        assertThat(css).contains("--app-feedback-warning-fill: var(--canary-100)");
        assertThat(css).contains("--app-feedback-negative-fill: var(--coral-20)");
        assertThat(css).contains("--app-feedback-negative-fill: var(--coral-100)");
        assertThat(css).contains("--app-feedback-success-fill: var(--mint-20)");
        assertThat(css).contains("--app-feedback-success-fill: var(--mint-100)");

        // Text needs the same treatment: the mid-tone validation colours read on light
        // surfaces but not on dark ones, and their -contrast variants vice versa.
        assertThat(css).contains("--app-feedback-negative-text: var(--colors-validation-lava)");
        assertThat(css).contains("--app-feedback-negative-text: var(--colors-validation-lava-contrast)");
        assertThat(css).contains("--app-feedback-success-text: var(--colors-validation-mint)");
        assertThat(css).contains("--app-feedback-success-text: var(--colors-validation-mint-contrast)");
        assertThat(css).contains("--app-feedback-warning-text: var(--canary-90)");
        assertThat(css).contains("--app-feedback-warning-text: var(--colors-validation-canary-contrast)");
    }

    @Test
    @DisplayName("apps never build a surface from a --colors-*-tint, which is light-mode only")
    void appsAvoidNonModeAwareTints() throws IOException {
        for (String path : APPS) {
            String html = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
            assertThat(html)
                .as("%s uses a light-only validation tint; use --app-feedback-*-fill instead", path)
                .doesNotContain("--colors-validation-lava-tint")
                .doesNotContain("--colors-validation-mint-tint")
                .doesNotContain("--colors-validation-canary-tint")
                .doesNotContain("--colors-validation-sky-tint");
        }
    }

    @Test
    @DisplayName("apps never colour text with a raw validation colour, which is not mode-aware")
    void appsUseModeAwareFeedbackText() throws IOException {
        for (String path : APPS) {
            String html = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
            // Only the `color` property matters. `border-color` may keep the mid-tone
            // validation colours, which read against both light and dark surfaces.
            assertThat(html)
                .as("%s sets text from a fixed validation colour; use --app-feedback-*-text instead", path)
                .doesNotContainPattern("(?<!border-)color:\\s*var\\(--colors-validation");
        }
    }

    @Test
    @DisplayName("apps never label a transport fill with --basecolors-text-light, which stays pale in dark mode")
    void appsInvertLabelsOnTransportFills() throws IOException {
        for (String path : APPS) {
            String html = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
            // Linje's dark-mode transport colours are pastels; a pale label on them is
            // unreadable. --basecolors-frame-default inverts with the surface instead.
            assertThat(html.replaceAll("(?m)^\\s*(//|/\\*|\\*).*$", ""))
                .as("%s uses --basecolors-text-light on a transport fill", path)
                .doesNotContain("var(--basecolors-text-light)");
        }
    }

    @Test
    @DisplayName("tokens the apps style against are present")
    void expectedTokensArePresent() throws IOException {
        String css = tokensCss();
        assertThat(css)
            .contains("--basecolors-frame-default")
            .contains("--basecolors-frame-elevated")
            .contains("--basecolors-text-accent")
            .contains("--basecolors-text-subdued")
            .contains("--basecolors-stroke-subdued")
            .contains("--basecolors-shape-bus-default")
            .contains("--basecolors-shape-train-default")
            .contains("--basecolors-shape-metro-default")
            .contains("--basecolors-shape-tram-default")
            .contains("--basecolors-shape-ferry-default")
            .contains("--basecolors-shape-plane-default")
            .contains("--basecolors-shape-walk")
            .contains("--basecolors-shape-mobility-default")
            .contains("--colors-validation-lava")
            .contains("--colors-validation-mint")
            .contains("--font-sizes-small")
            .contains("--border-radiuses-medium")
            .contains("--shadows-card-shadow")
            .contains("--timings-fast")
            .contains("--z-indexes-popover");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "app/departures-board.html",
        "app/trip-map.html",
        "app/nearby-stops-map.html",
        "app/nearby-mobility-map.html",
    })
    @DisplayName("each app declares the token marker and colour-scheme support")
    void appsDeclareMarkerAndColorScheme(String path) throws IOException {
        String html = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).as("token injection marker").contains(AppHtmlLoader.TOKEN_MARKER);
        assertThat(html)
            .as("color-scheme meta prevents a flash of un-themed content")
            .contains("<meta name=\"color-scheme\" content=\"light dark\">");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "app/trip-map.html",
        "app/nearby-stops-map.html",
        "app/nearby-mobility-map.html",
    })
    @DisplayName("map apps load both the Leaflet stylesheet and its script")
    void mapAppsLoadLeaflet(String path) throws IOException {
        String html = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        // Dropping the script leaves L undefined and the app renders only an error,
        // which no server-side test would otherwise catch.
        assertThat(html).as("Leaflet stylesheet").containsPattern("leaflet@[\\d.]+/dist/leaflet\\.css");
        assertThat(html).as("Leaflet script").containsPattern("leaflet@[\\d.]+/dist/leaflet\\.js");
    }

    @Test
    @DisplayName("served app HTML has the tokens inlined, since a relative <link> has no origin in the app iframe")
    void loaderInlinesTokensIntoEveryApp() throws IOException {
        AppHtmlLoader loader = loader();

        for (String path : APPS) {
            Resource resource = new ClassPathResource(path);
            String served = loader.load(resource);

            assertThat(served).as("%s marker replaced", path).doesNotContain(AppHtmlLoader.TOKEN_MARKER);
            assertThat(served).as("%s carries token definitions", path).contains("--basecolors-frame-default");
            assertThat(served).as("%s keeps its own markup", path).contains("</html>");
        }
    }

    @Test
    @DisplayName("travel CSS keeps only its own component layer, leaving tokens the single source of truth")
    void travelCssIsStrippedOfDuplicatedBaseTokens() throws IOException {
        String tokens = tokensCss();
        String travel = travelCss();

        Set<String> baseTokens = new HashSet<>();
        Matcher b = DEFINITION.matcher(tokens);
        while (b.find()) {
            baseTokens.add(b.group(1));
        }

        Set<String> redefined = new TreeSet<>();
        Set<String> componentTokens = new HashSet<>();
        Matcher t = DEFINITION.matcher(travel);
        while (t.find()) {
            String name = t.group(1);
            if (name.startsWith("--components-")) {
                componentTokens.add(name);
            } else if (baseTokens.contains(name)) {
                redefined.add(name);
            }
        }

        assertThat(componentTokens).as("--components-* layer").hasSizeGreaterThan(100);
        assertThat(redefined)
            .as("base tokens duplicated from the package's own copy — the strip step regressed")
            .isEmpty();
    }

    @Test
    @DisplayName("travel components respond to colour mode without JavaScript")
    void travelCssHasDarkModeFallback() throws IOException {
        String travel = travelCss();
        assertThat(travel).contains("@media (prefers-color-scheme: dark)");
        assertThat(travel).contains(":root:not([data-color-mode='light'])");
    }

    @Test
    @DisplayName("the travel classes the apps actually build markup against are present")
    void travelCssCarriesTheClassesTheAppsUse() throws IOException {
        String travel = travelCss();
        assertThat(travel)
            .contains(".eds-travel-header__from")
            .contains(".eds-travel-header__to")
            .contains(".eds-travel-tag")
            .contains(".eds-leg-bone--vertical")
            .contains(".eds-leg-bone__start")
            .contains(".eds-leg-bone__stop")
            .contains(".eds-leg-line--vertical")
            .contains(".eds-leg-line--dotted");
        // TravelTag fills are addressed by modality; the apps build these names from OTP modes.
        assertThat(travel)
            .contains("--components-travel-traveltag-standard-fill-bus")
            .contains("--components-travel-traveltag-standard-fill-rail")
            .contains("--components-travel-traveltag-standard-fill-walk");
    }

    @Test
    @DisplayName("only the journey apps opt into the travel stylesheet")
    void travelMarkerIsScopedToJourneyApps() throws IOException {
        for (String path : TRAVEL_APPS) {
            assertThat(new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8))
                .as("%s should carry the travel marker", path)
                .contains(AppHtmlLoader.TRAVEL_MARKER);
        }
        for (String path : new String[]{"app/nearby-stops-map.html", "app/nearby-mobility-map.html"}) {
            assertThat(new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8))
                .as("%s does not render journeys and should not pay for the travel CSS", path)
                .doesNotContain(AppHtmlLoader.TRAVEL_MARKER);
        }
    }

    @Test
    @DisplayName("the travel stylesheet is inlined after the tokens it resolves against")
    void travelCssIsInlinedAfterTokens() throws IOException {
        AppHtmlLoader loader = loader();

        for (String path : TRAVEL_APPS) {
            String served = loader.load(new ClassPathResource(path));

            assertThat(served).as("%s travel marker replaced", path)
                .doesNotContain(AppHtmlLoader.TRAVEL_MARKER);
            assertThat(served).as("%s carries travel components", path)
                .contains(".eds-leg-bone");

            // Component tokens resolve against the base layer, so order matters.
            assertThat(served.indexOf("--basecolors-frame-default"))
                .as("%s must inline tokens before travel", path)
                .isLessThan(served.indexOf("--components-travel-traveltag-standard-fill-bus"));
        }
    }

    @Test
    @DisplayName("the trip map can render situations in the requested language")
    void tripMapHandlesSituations() throws IOException {
        String html = new ClassPathResource("app/trip-map.html").getContentAsString(StandardCharsets.UTF_8);

        // OTP returns every translation of a summary, so the app has to pick by language
        // rather than take the first entry.
        assertThat(html).as("language-aware summary lookup").contains("x?.language === code");
        // The same disruption is attached to every leg it touches.
        assertThat(html).as("de-duplication by situation number").contains("situationNumber");
        assertThat(html).contains("shownSituations");
        assertThat(html).as("severity styling").contains("leg-situation--severe");

        for (String key : new String[]{"disruption:", "disruption_one:", "disruption_count:"}) {
            // one per supported locale
            assertThat(html.split(java.util.regex.Pattern.quote(key), -1).length - 1)
                .as("%s should be translated for en, nb and nn", key)
                .isEqualTo(3);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "app/trip-map.html",
        "app/departures-board.html",
        "app/nearby-stops-map.html",
        "app/nearby-mobility-map.html",
    })
    @DisplayName("every locale in an app defines the same i18n keys")
    void localesAreComplete(String path) throws IOException {
        String html = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);

        // The i18n table is a plain object literal per locale. A key added to one and
        // forgotten in another shows up as the raw key name in the UI, which nothing
        // server-side would otherwise catch.
        Matcher block = Pattern
            .compile("\\b(en|nb|nn):\\s*\\{(.*?)\\n\\s*\\},", Pattern.DOTALL)
            .matcher(html);

        Map<String, Set<String>> byLocale = new LinkedHashMap<>();
        while (block.find()) {
            Set<String> keys = new TreeSet<>();
            Matcher key = Pattern.compile("(?m)^\\s*([a-z_0-9]+)\\s*:").matcher(block.group(2));
            while (key.find()) {
                keys.add(key.group(1));
            }
            byLocale.put(block.group(1), keys);
        }

        assertThat(byLocale).as("%s should define en, nb and nn", path).hasSize(3);
        Set<String> english = byLocale.get("en");
        assertThat(english).as("%s en keys", path).isNotEmpty();
        for (Map.Entry<String, Set<String>> locale : byLocale.entrySet()) {
            assertThat(locale.getValue())
                .as("%s locale '%s' differs from en", path, locale.getKey())
                .isEqualTo(english);
        }
    }

    @Test
    @DisplayName("the trip map collapses an empty result instead of reserving a viewport")
    void tripMapCollapsesEmptyResults() throws IOException {
        String html = new ClassPathResource("app/trip-map.html").getContentAsString(StandardCharsets.UTF_8);

        // The host sizes the iframe from the content it is told about, so a min-height of
        // 100vh turns a no-results answer into a screenful - once per retry.
        assertThat(html).as("no viewport-height floor").doesNotContain("min-height: 100vh");
        assertThat(html).contains("is-empty");
        assertThat(html).contains("setEmpty(true)");
        assertThat(html).contains("setEmpty(false)");
    }

    @Test
    @DisplayName("an app without the marker is served unchanged rather than failing")
    void appWithoutMarkerIsServedUnchanged() throws IOException {
        AppHtmlLoader loader = loader();

        Resource plain = new org.springframework.core.io.ByteArrayResource(
            "<html><body>no marker</body></html>".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(loader.load(plain)).isEqualTo("<html><body>no marker</body></html>");
    }
}
