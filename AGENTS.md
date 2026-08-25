# CLAUDE.md

## Project Overview

MCP server for OpenTripPlanner's transmodel GraphQL API. Java/Spring Boot with MCP Apps interactive UIs. Enables AI agents to access Norwegian/Nordic public transport trip planning, real-time departures, and geocoding via MCP protocol with embedded departure board and trip map UIs.

## Key patterns

- UI tools return `CallToolResult`: slim JSON in `content[0].text` for the model, UI-only fields in
  `_meta["org.entur/ui"]` via `UiPayload.split()`. `geocode` and `alerts` still return plain `String`.
- Each app HTML file carries an identical `graftUiMeta()` helper that puts the `_meta` fields back
  before rendering, and degrades gracefully when `_meta` is absent (hosts may strip it)
- `trip` accepts `transportModes`; when set, the OTP `modes:` block MUST also carry
  `accessMode`/`egressMode`/`directMode: foot` or OTP returns zero trip patterns
- `nearby-mobility` hoists repeated `system` objects into a top-level `systems` map keyed by system id;
  items carry `systemId` instead of `system`
- `src/main/resources/app/entur-tokens.css` and `entur-travel.css` are **generated — do not hand-edit**.
  Regenerate with `scripts/update-entur-css.py`.
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
- `trip` renders service disruptions: a banner per affected leg in the expanded card and a count on
  every option. OTP returns each summary in all languages and repeats a disruption across every leg
  it touches, so the app picks by `language` and de-duplicates on `situationNumber`.
- `language` parameter (`required = true`) on `trip`, `departures`, `nearby-stops` — detect from conversation context; `LanguageUtil.normalize()` accepts `en`, `nb`, `nn`, defaults to `en`
