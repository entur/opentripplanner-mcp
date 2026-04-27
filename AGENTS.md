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
Tools (src/main/java/.../tools/TripSearchTool.java)
    ├── trip + TripUiMeta              → OTP GraphQL API (trip planning) + trip-map UI
    ├── departures + DeparturesUiMeta  → OTP GraphQL API (departures) + departure-board UI
    ├── nearby-stops + NearbyStopsUiMeta → OTP GraphQL API (nearby stops) + nearby-stops-map UI
    ├── alerts (text-only)             → OTP GraphQL API (service disruptions)
    ├── geocode (text-only)            → Entur Geocoder REST API
    ├── poll-trip (app-only)           → delegates to trip(), called by trip-map UI
    └── poll-departures (app-only)     → delegates to departures(), called by departure-board UI
Services (src/main/java/.../services/)
    ├── OtpSearchService   → OTP GraphQL API
    └── GeocoderService    → Entur Geocoder REST API
UI apps (src/main/resources/app/)
    ├── departures-board.html  → departure board (served via @McpResource)
    ├── trip-map.html          → trip options viewer (served via @McpResource)
    └── nearby-stops-map.html  → nearby stops viewer (served via @McpResource)
```

**Seven MCP tools** (5 model-visible + 2 app-only):
- `trip` — multi-leg route planning with trip map UI
- `departures` — real-time departure board with interactive UI
- `nearby-stops` — nearby stops within a radius with map UI
- `alerts` — active service disruptions/cancellations (text-only, filterable by severity)
- `geocode` — place name/address to coordinates (text-only)
- `poll-departures` — app-only auto-refresh for departure board
- `poll-trip` — app-only re-plan for trip map

**Key patterns:**
- Tools return JSON text content (success data or serialized `ErrorResponse`)
- `@McpTool` + `@McpResource` from `org.springframework.ai.mcp.annotation` (Spring AI 2.0.0-M3)
- `MetaProvider` inner classes set `_meta.ui` (resourceUri, csp, visibility)
- App-only tools use `AppOnlyMeta` → `_meta.ui.visibility: ["app"]`
- UI apps are plain HTML files served as classpath resources (no build step)
- Client-side `App` from `@modelcontextprotocol/ext-apps@0.4.2` (unpkg) handles `ontoolresult`, `callServerTool`
- `resolveStopId()` passes through `NSR:StopPlace:*`/`NSR:Quay:*` IDs, geocodes everything else
- `geocodeIfNeeded()` parses `"lat,lng"` coordinates or falls back to geocoder API
- Input validation with custom exception classes (`ValidationException`, `GeocodingException`, etc.)
- Stateless MCP transport (`spring.ai.mcp.server.protocol=STATELESS`)
- `language` parameter (`required = true`) on `trip`, `departures`, `nearby-stops` — detect from conversation context; `LanguageUtil.normalize()` accepts `en`, `nb`, `nn`, defaults to `en`

**External APIs (application.properties):**
- `org.entur.otp.url` (default: `https://api.dev.entur.io/journey-planner/v3/graphql`)
- `org.entur.geocoder.url` (default: `https://api.dev.entur.io/geocoder/v2/autocomplete`)
- `org.entur.mcp.client_name` (default: `entur-mcp`) — sent as `ET-Client-Name` header

**Testing patterns:**
- `@SpringBootTest` integration tests + `mockwebserver` for HTTP mocking
- Unit tests with plain JUnit 5 + Mockito
- Test files in `src/test/` mirror `src/main/` structure~~~~