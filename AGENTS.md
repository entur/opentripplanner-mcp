# CLAUDE.md

## Project Overview

MCP server for OpenTripPlanner's transmodel GraphQL API. Java/Spring Boot with MCP Apps interactive UIs. Enables AI agents to access Norwegian/Nordic public transport trip planning, real-time departures, and geocoding via MCP protocol with embedded departure board and trip map UIs.

## Key patterns

- UI tools return `CallToolResult`: slim JSON in `content[0].text` for the model, UI-only fields in
  `_meta["org.entur/ui"]` via `UiPayload.split()`. `geocode` and `alerts` still return plain `String`.
- Each app HTML file carries an identical `graftUiMeta()` helper that puts the `_meta` fields back
  before rendering, and degrades gracefully when `_meta` is absent (hosts may strip it)
- `trip` accepts `transportModes`; when set, the OTP `modes:` block MUST also carry
  `accessMode`/`egressMode`/`directMode: foot` or OTP returns zero trip patterns. A `rail` filter
  also sends `bus` with submode `railReplacementBus`: OTP filters on each trip's own mode, and a
  replaced train is a bus trip on the rail line, so `rail` alone finds nothing during a closure.
  `departures` does the same, but its `whiteListedModes` takes no submodes: it fetches buses too
  (asking for 4x the departures) and drops every bus that is not a replacement bus
- `trip` sends stops to OTP by ID in `place`, not as coordinates: an NSR ID input skips the
  geocoder, and a geocoded stop keeps its ID. A coordinate must first be linked to OTP's street
  network, which fails where there is none (the Fjord Line terminals in Denmark). OTP then
  reports `locationNotFound`, which the MCP turns into an error rather than an empty result.
  `nearby-stops` is the opposite: `nearest` fails with a `LOCATION_NOT_FOUND` DataFetchingException
  for such a point, and the MCP answers with no stops (`no_results`), since none exist there for OTP.
- `trip` reports the stop each name or stop ID stands for in `resolvedLocations` (name and
  stop ID; a stop ID's name comes from OTP). Coordinates get no entry. The geocoder's first hit
  is not always the place meant ("Hirtshals Fjordline terminal" ranks the Color Line terminal
  first), so the UI header shows the resolved name and the `noTripsFound` advice names the stops
  and points the model at `geocode`. `query.from`/`to` stay as typed: re-planning resends them.
- `nearby-mobility` hoists repeated `system` objects into a top-level `systems` map keyed by system id;
  items carry `systemId` instead of `system`
- `src/main/resources/app/entur-tokens.css` and `entur-travel.css` are **generated — do not hand-edit**.
  Regenerate with `scripts/update-entur-css.py`.
- `src/main/resources/app/vendor/` (Leaflet, the MCP Apps SDK) is likewise **generated** by
  `scripts/update-vendor-js.py` and inlined by `AppHtmlLoader`. Apps must load no script from a
  CDN: app script can call tools and post into the conversation. Upgrades change the pinned
  versions and integrity hashes in that script.
- App UI URIs are versioned (`ui://trip/<hash>/trip-map.html`) and registered in code by
  `AppResources`, not with `@McpResource`. Hosts cache an app's HTML by URI, so a fixed URI kept
  an old UI on screen across deploys; the hash covers every app file, so any UI change gets a new
  URI. A template serves any version, so older conversations still get a UI. Tools take their
  `resourceUri` from `AppResources.App`; never write a `ui://` URI by hand.
- UI/styling conventions (Entur Linje tokens, colour mode, stylesheet inlining, previewing the apps)
  live in `src/main/resources/app/CLAUDE.md`.
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
- The departure board auto-refreshes (`poll-departures`, every 10s) only while it can be seen: the
  host page is visible, the board is in the viewport (IntersectionObserver), and there was user
  activity or a new tool result in the last 15 minutes. A board left open polled for hours otherwise.
  It shows "Live updates paused" and refreshes at once on resume.
- `trip` renders service disruptions: a banner per affected leg in the expanded card and a count on
  every option. OTP returns each summary in all languages and repeats a disruption across every leg
  it touches, so the app picks by `language` and de-duplicates on `situationNumber`.
- `language` parameter (`required = true`) on `trip`, `departures`, `nearby-stops` — detect from conversation context; `LanguageUtil.normalize()` accepts `en`, `nb`, `nn`, defaults to `en`
- Deployed pods do NOT read `src/main/resources/application.properties`: `JDK_JAVA_OPTIONS` sets
  `spring.config.location` to the file from `helm/opentripplanner-mcp/templates/configmap.yaml`,
  which replaces it. A setting needed in the cluster must be added to the configmap as well.
- What the user typed (place names, addresses, coordinates, stop IDs) is logged at DEBUG only,
  never INFO/WARN/ERROR: it can be a home address or a live position. Exception messages that
  quote it (geocoding, `locationNotFound`) go to the model but reach the logs via `ToolLog` at
  DEBUG. Usage is measured by `ToolMetrics` instead: `mcp.tool.calls` (timer per tool/outcome/
  client_type/language), `mcp.trip.locations` (name/stop_id/coordinates) and
  `mcp.trip.searches`. Every tag comes from a fixed set; never tag a metric with user input.
  `poll-trip`/`poll-departures` count as their own tools, which measures UI interaction.
