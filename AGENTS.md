# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MCP server for OpenTripPlanner's transmodel GraphQL API. Java/Spring Boot with MCP Apps interactive UIs. Enables AI agents to access Norwegian/Nordic public transport trip planning, real-time departures, and geocoding via MCP protocol with embedded departure board and trip map UIs.

## Build & Test Commands

```bash
mvn compile   # Compile
mvn test      # Run all tests
mvn package   # Build JAR
```

## Architecture

**Single MCP interface** with interactive UI apps:
- **MCP** (`POST /mcp`): Stateless streamable HTTP transport for AI agents
- **Health probes**: `GET /actuator/health` (port 9090)

```
Tools
    TripSearchTool.java
        ├── trip + TripUiMeta              → OTP GraphQL API (trip planning) + trip-map UI
        ├── departures + DeparturesUiMeta  → OTP GraphQL API (departures) + departure-board UI
        ├── nearby-stops + NearbyStopsUiMeta → OTP GraphQL API (nearby stops) + nearby-stops-map UI
        ├── alerts (text-only)             → OTP GraphQL API (service disruptions)
        ├── geocode (text-only)            → Entur Geocoder REST API
        ├── poll-trip (app-only)           → delegates to trip(), called by trip-map UI
        └── poll-departures (app-only)     → delegates to departures(), called by departure-board UI
    MobilityTool.java
        └── nearby-mobility + NearbyMobilityUiMeta → Entur Mobility GraphQL API (vehicles + stations) + nearby-mobility-map UI
    McpMetaProviders.java                  → shared CSP/UI/AppOnly MetaProvider helpers
Services (src/main/java/.../services/)
    ├── OtpSearchService     → OTP GraphQL API
    ├── GeocoderService      → Entur Geocoder REST API
    └── MobilityService      → Entur Mobility GraphQL API
UI apps (src/main/resources/app/)
    ├── departures-board.html     → departure board (served via @McpResource)
    ├── trip-map.html             → trip options viewer (served via @McpResource)
    ├── nearby-stops-map.html     → nearby stops viewer (served via @McpResource)
    ├── nearby-mobility-map.html  → shared-mobility viewer (served via @McpResource)
    ├── entur-tokens.css          → vendored Entur Linje design tokens (generated)
    └── entur-travel.css          → vendored Entur Linje travel components (generated)
AppHtmlLoader.java                → inlines the vendored CSS into each app at serve time
```

**Eight MCP tools** (6 model-visible + 2 app-only):
- `trip` — multi-leg route planning with trip map UI
- `departures` — real-time departure board with interactive UI
- `nearby-stops` — nearby stops within a radius with map UI
- `nearby-mobility` — closest shared-mobility vehicles and rental stations within a radius with map UI
- `alerts` — active service disruptions/cancellations (text-only, filterable by severity)
- `geocode` — place name/address to coordinates (text-only)
- `poll-departures` — app-only auto-refresh for departure board
- `poll-trip` — app-only re-plan for trip map

**Key patterns:**
- UI tools return `CallToolResult`: slim JSON in `content[0].text` for the model, UI-only fields in
  `_meta["org.entur/ui"]` via `UiPayload.split()`. `geocode` and `alerts` still return plain `String`.
- Each app HTML file carries an identical `graftUiMeta()` helper that puts the `_meta` fields back
  before rendering, and degrades gracefully when `_meta` is absent (hosts may strip it)
- `trip` accepts `transportModes`; when set, the OTP `modes:` block MUST also carry
  `accessMode`/`egressMode`/`directMode: foot` or OTP returns zero trip patterns
- `nearby-mobility` hoists repeated `system` objects into a top-level `systems` map keyed by system id;
  items carry `systemId` instead of `system`
- `@McpTool` + `@McpResource` from `org.springframework.ai.mcp.annotation` (Spring AI 2.0.0-M7)
- `MetaProvider` inner classes set `_meta.ui` (resourceUri, csp, visibility)
- App-only tools use `AppOnlyMeta` → `_meta.ui.visibility: ["app"]`
- UI apps are plain HTML files served as classpath resources (no build step)
- **Design tokens:** the apps style against Entur Linje tokens (https://linje.entur.no). Only
  `@entur/tokens` is vendored — plain CSS custom properties, framework-agnostic. The React
  `@entur/*` component packages are deliberately NOT used; there is no build step to consume them.
- `@entur/travel` is also vendored, giving `trip` and `departures` the real TravelHeader,
  TravelTag and LegBone rather than hand-rolled lookalikes. Its `.eds-*` class names are an
  implementation detail, not a public API, so the version is pinned and an upgrade needs the
  rendered output checked. Colour is passed through the components' own `--background-color` /
  `--text-color` custom properties, which is the stable half of the contract.
- Both files are **generated — do not hand-edit**. Regenerate with `scripts/update-entur-css.py`,
  which inlines the packages' bare-specifier `@import`s (browsers cannot resolve them), derives a
  `prefers-color-scheme` fallback, strips each component package's duplicated copy of the base
  tokens, and adds an `--app-feedback-*` layer for the mode-aware surfaces Linje only ships in
  `@entur/alert`.
- Stylesheets are inlined by `AppHtmlLoader` at markers in each app's `<head>`:
  `<!--ENTUR_TOKENS-->` in all four, `<!--ENTUR_TRAVEL-->` in `trip-map` and `departures-board`.
  A relative `<link>` cannot be used: the host renders the app in a sandboxed iframe with no origin
  to resolve against.
- `scripts/preview-apps.py` renders the apps in a normal browser (light and dark) using real tool
  results pulled over `/mcp`, which is the only practical way to eyeball UI changes.
- **Colour mode:** apps set `data-color-mode` on `<html>` from `app.getHostContext()?.theme` and keep
  it current via `app.onhostcontextchanged`. With no attribute, the generated `prefers-color-scheme`
  block decides, so theming works before the host reports anything and without JS.
- Token rules worth knowing, all enforced by `EnturCssTest`:
  - Only `--basecolors-*` are mode-aware. `--colors-validation-*` and its `-tint`/`-contrast`
    variants are fixed light-mode values — use `--app-feedback-*-fill` / `--app-feedback-*-text`.
  - Transport fills are saturated in light mode and pastel in dark, so labels on them use
    `--basecolors-frame-default` (inverts with the surface), never `--basecolors-text-light`.
  - Leaflet writes colours into SVG presentation attributes, which do not accept `var()`. Vector
    layers resolve tokens through the local `cssVar()` helper and re-render on colour-mode change.
  - TravelTag fills are named after OTP's own mode strings
    (`--components-travel-traveltag-standard-fill-{bus,rail,water,air,…}`), so most of the mode
    mapping is a pass-through; `coach`→`bus`, `subway`→`metro`, `foot`→`walk`.
- An empty `trip` result carries a top-level `noTripsFound` advisory telling the model the
  query was valid and that repeating it changes nothing. Without it the model sees a bare empty
  array, re-issues the same search, and each retry renders another UI card. The apps set no
  `min-height` for the same reason: the host sizes the iframe from the content, so a no-results
  answer collapses to a line rather than a screenful.
- When a search with a `departureTime` finds nothing, `OtpSearchService` re-asks once with an
  explicit `searchWindow: 1440` and reports `nextDepartureTime`. OTP's default window is short
  and adaptive, so an overnight gap simply returns nothing; a day-long window finds the far side
  of it. Deliberately a second request rather than a wider first one, which would slow the
  common case that already has results. It is best-effort: a failure there must not turn a valid
  empty result into an error. The trip UI offers the time as a button that re-plans via
  `poll-trip`.
- `trip` renders service disruptions: a banner per affected leg in the expanded card and a count on
  every option. OTP returns each summary in all languages and repeats a disruption across every leg
  it touches, so the app picks by `language` and de-duplicates on `situationNumber`.
- Client-side `App` from `@modelcontextprotocol/ext-apps@0.4.2` (unpkg) handles `ontoolresult`, `callServerTool`
- `resolveStopId()` passes through `NSR:StopPlace:*`/`NSR:Quay:*` IDs, geocodes everything else
- `geocodeIfNeeded()` parses `"lat,lng"` coordinates or falls back to geocoder API
- Input validation with custom exception classes (`ValidationException`, `GeocodingException`, etc.)
- Stateless MCP transport (`spring.ai.mcp.server.protocol=STATELESS`)
- `language` parameter (`required = true`) on `trip`, `departures`, `nearby-stops` — detect from conversation context; `LanguageUtil.normalize()` accepts `en`, `nb`, `nn`, defaults to `en`

**External APIs (application.properties):**
- `org.entur.otp.url` (default: `https://api.dev.entur.io/journey-planner/v3/graphql`)
- `org.entur.geocoder.url` (default: `https://api.dev.entur.io/geocoder/v2/autocomplete`)
- `org.entur.mobility.url` (default: `https://api.entur.io/mobility/v2/graphql`)
- `org.entur.mcp.client_name` (default: `entur-mcp`) — sent as `ET-Client-Name` header

**Testing patterns:**
- `@SpringBootTest` integration tests + `mockwebserver` for HTTP mocking
- Unit tests with plain JUnit 5 + Mockito
- Test files in `src/test/` mirror `src/main/` structure~~~~