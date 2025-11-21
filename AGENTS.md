# AGENTS.md

This file provides guidance to AI agents when working with code in this repository.

## Project Overview

This is a Model Context Protocol (MCP) server for OpenTripPlanner's transmodel GraphQL API, implemented in Java using Spring Boot and [Spring AI's MCP framework](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html). It enables AI agents to access Norwegian/Nordic public transport trip planning through a standardized MCP interface.

## Development Commands

### Building and Running

```bash
# Build the project
mvn clean package

# Run the application (development mode - uses dev API endpoints)
mvn spring-boot:run

# Run with production API endpoints
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=prod
```

The server starts on port 8080 (MCP at `/mcp`) with management endpoints on port 9090.

### Running Tests

```bash
# Run all tests
mvn test

# Run only unit tests (excludes integration tests)
mvn test -Dtest=*Test -Dtest=!*IntegrationTest

# Run only integration tests (requires network access)
mvn test -Dtest=*IntegrationTest

# Run a specific test class
mvn test -Dtest=TripSearchToolTest
```

### Docker Deployment

```bash
# Build JAR first, then Docker image
mvn clean package
docker build -t opentripplanner-mcp:latest .

# Run container
docker run -p 8080:8080 opentripplanner-mcp:latest
```

## Architecture

### MCP Server Structure

The server uses Spring AI's MCP annotations framework for automatic JSON-RPC protocol handling over HTTP. Unlike stdio-based MCP servers, this exposes an HTTP endpoint at `http://localhost:8080/mcp`.

**Key components**:

- **App.java**: Spring Boot application entry point
- **tools/TripSearchTool.java**: MCP tool definitions using `@McpTool` annotations
- **services/OtpSearchService.java**: Trip planning via OpenTripPlanner GraphQL API
- **services/GeocoderService.java**: Location geocoding via Entur's geocoder REST API
- **validation/InputValidator.java**: Input validation with structured error responses
- **exception/**: Custom exceptions (ValidationException, GeocodingException, TripPlanningException)
- **model/**: Domain models (Location, ErrorResponse)

### MCP Tool Implementation

Tools are defined using Spring AI community annotations:

```java
@McpTool(name = "trip", description = "...")
public String trip(
    @McpToolParam(description = "...", required = true) String from,
    @McpToolParam(description = "...", required = false) String departureTime
) { ... }
```

Each tool returns a JSON string (success response or ErrorResponse serialized to JSON).

### External API Integration

**OpenTripPlanner GraphQL API**:
- Dev: `https://api.dev.entur.io/journey-planner/v3/graphql`
- Prod: `https://api.entur.io/journey-planner/v3/graphql`
- Uses POST with JSON body containing GraphQL query
- Requires `ET-Client-Name` header

**Geocoder API**:
- Dev/Prod: `https://api.dev.entur.io/geocoder/v2/autocomplete`
- Uses GET with URL-encoded `text` parameter
- Returns GeoJSON FeatureCollection

### Tool Handlers

**trip tool**:
- Required: `from`, `to` (address, place name, or "lat,lng" coordinates)
- Optional: `departureTime`, `arrivalTime` (ISO format), `maxResults` (default: 3, max: 50)
- Cannot specify both departureTime and arrivalTime
- Auto-geocodes location strings to coordinates before querying OTP

**geocode tool**:
- Required: `text`
- Optional: `maxResults` (default: 10, max: 50)
- Returns GeoJSON FeatureCollection with coordinates and place names

## Configuration

Key properties in `application.properties`:

- `server.port`: HTTP port (default: 8080)
- `management.server.port`: Actuator/metrics port (default: 9090)
- `org.entur.otp.url`: OpenTripPlanner GraphQL endpoint
- `org.entur.geocoder.url`: Entur Geocoder REST API URL
- `org.entur.mcp.client_name`: Client identifier for API requests (default: "entur-mcp")

Environment variable equivalents use uppercase with underscores (e.g., `ORG_ENTUR_OTP_URL`).

## Testing with AI Agents

1. Start the server: `mvn spring-boot:run`
2. Connect MCP client to `http://localhost:8080/mcp`
3. Test: "Plan a trip from Oslo to Bergen"

## Metrics

Prometheus metrics exposed at `http://localhost:9090/actuator/prometheus`:
- `mcp.trip.service` - Trip request timing
- `mcp.geocoder.service` - Geocoder request timing
