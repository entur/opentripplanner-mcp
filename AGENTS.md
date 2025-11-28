# AGENTS.md

This file provides guidance to AI agents when working with code in this repository.

## Project Overview

This is a Model Context Protocol (MCP) server for OpenTripPlanner's transmodel GraphQL API, implemented in Java using Spring Boot and [Spring AI's MCP framework](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html). It enables AI agents to access Norwegian/Nordic public transport trip planning, real-time departure information, and location geocoding through a standardized MCP interface. The server provides occupancy status and situation messages (service alerts/disruptions) for transit services.

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

**Dual Interface Design**: The server provides the same functionality through two interfaces:
1. **MCP Interface** (`/mcp`): For AI agents and MCP clients using JSON-RPC protocol
2. **REST API** (`/api/*`): For traditional HTTP clients with OpenAPI documentation

Both interfaces share the same service layer (OtpSearchService, GeocoderService) ensuring consistent behavior.

**Key components**:

- **App.java**: Spring Boot application entry point
- **tools/TripSearchTool.java**: MCP tool definitions using `@McpTool` annotations (trip, departures, geocode)
- **rest/TripPlannerRestController.java**: REST API endpoints for HTTP/OpenAPI access (same functionality as MCP tools)
- **rest/HealthApi.java**: Kubernetes-compatible health check endpoints (/readiness, /liveness)
- **rest/GlobalExceptionHandler.java**: Global exception handling for REST endpoints
- **services/OtpSearchService.java**: Trip planning and departure board queries via OpenTripPlanner GraphQL API
- **services/GeocoderService.java**: Location geocoding and stop resolution via Entur's geocoder REST API
- **validation/InputValidator.java**: Input validation with structured error responses
- **dto/request/**: Request DTOs for REST API validation (TripRequest, DepartureRequest, GeocodeRequest)
- **config/OpenApiConfig.java**: OpenAPI/Swagger configuration with multi-environment server definitions
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

The server exposes three MCP tools:
- **trip**: Plan multi-leg public transport routes between two locations with occupancy and situation messages
- **departures**: Get real-time departures from a single stop or station with live delay information
- **geocode**: Convert place names or addresses into geographic coordinates

## REST API (OpenAPI)

In addition to the MCP interface, the server also exposes a REST API documented with OpenAPI/Swagger. This allows traditional HTTP clients to access the same functionality.

### API Documentation

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec**: `http://localhost:8080/api/openapi`

The API is versioned and available on multiple environments:
- **Production**: `https://api.entur.io/journey-planner-mcp/v1`
- **Staging**: `https://api.staging.entur.io/journey-planner-mcp/v1`
- **Development**: `https://api.dev.entur.io/journey-planner-mcp/v1`
- **Local**: `http://localhost:8080`

### REST Endpoints

#### `GET /api/trips`

Plan a multi-modal journey between two locations.

**Query Parameters**:
- `from` (required, string, max 500 chars): Starting location (address, place name, or lat,lng coordinates)
  - Example: `Oslo S`
- `to` (required, string, max 500 chars): Destination location (address, place name, or lat,lng coordinates)
  - Example: `Bergen stasjon`
- `departureTime` (optional, ISO 8601 string): Departure time
  - Example: `2023-05-26T12:00:00`
- `arrivalTime` (optional, ISO 8601 string): Arrival time
  - Example: `2023-05-26T14:00:00`
- `maxResults` (optional, integer, 1-50, default: 3): Maximum number of trip options to return

**Example Request**:
```bash
curl "http://localhost:8080/api/trips?from=Oslo%20S&to=Bergen%20stasjon&maxResults=3"
```

**Response Codes**:
- `200`: Trip plan successfully generated
- `400`: Invalid request parameters (validation error or geocoding failure)
- `500`: Trip planning service error

#### `GET /api/departures`

Get real-time departures from a single stop or station.

**Query Parameters**:
- `stop` (required, string, max 500 chars): Stop place name or NSR ID
  - Example: `Oslo S` or `NSR:StopPlace:337`
- `numberOfDepartures` (optional, integer, 1-50, default: 10): Number of departures to return
- `startTime` (optional, ISO 8601 string, default: now): Start time for search
  - Example: `2023-05-26T12:00:00`
- `timeRangeMinutes` (optional, integer, 1-1440, default: 60): Time range in minutes to search (max 24 hours)
- `transportModes` (optional, array of strings): Filter by transport modes
  - Values: `rail`, `bus`, `tram`, `metro`, `water`, `air`
  - Example: `transportModes=rail&transportModes=bus`

**Example Request**:
```bash
curl "http://localhost:8080/api/departures?stop=Oslo%20S&numberOfDepartures=10"
```

**Response Codes**:
- `200`: Departures successfully retrieved
- `400`: Invalid request parameters or stop not found
- `500`: Departure board service error

#### `GET /api/geocode`

Convert place names or addresses into geographic coordinates.

**Query Parameters**:
- `text` (required, string, max 500 chars): Location text to search for
  - Example: `Oslo rådhus`
- `maxResults` (optional, integer, 1-50, default: 10): Maximum number of results to return

**Example Request**:
```bash
curl "http://localhost:8080/api/geocode?text=Oslo%20rådhus&maxResults=5"
```

**Response Codes**:
- `200`: Location successfully geocoded
- `400`: Invalid request parameters
- `500`: Geocoding service error

### Health Check Endpoints

The server provides Kubernetes-compatible health check endpoints:

- **`GET /readiness`**: Returns `OK` when the service is ready to accept traffic
- **`GET /liveness`**: Returns `OK` when the service is alive

These endpoints are not included in the OpenAPI documentation and return plain text responses.

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
- Returns trip patterns with occupancy status and situation messages (service alerts/disruptions)

**departures tool**:
- Required: `stop` (stop place name or NSR ID, e.g., "Oslo S" or "NSR:StopPlace:337")
- Optional: `numberOfDepartures` (default: 10, max: 50), `startTime` (ISO format, default: now), `timeRangeMinutes` (default: 60, max: 1440), `transportModes` (list of modes to filter by)
- Returns real-time departure information with line numbers, destinations, platforms, delays, occupancy status, and situation messages
- Auto-resolves stop names to NSR IDs using the geocoder service

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

## Testing

### Testing with AI Agents (MCP)

1. Start the server: `mvn spring-boot:run`
2. Connect MCP client to `http://localhost:8080/mcp`
3. Test examples:
   - "Plan a trip from Oslo to Bergen"
   - "When is the next bus from Oslo S?"
   - "Show me departures from Bergen stasjon"
   - "Find coordinates for Trondheim"

### Testing with REST API

1. Start the server: `mvn spring-boot:run`
2. Open Swagger UI: `http://localhost:8080/swagger-ui.html`
3. Try the example curl commands listed in the REST Endpoints section above
4. Or use the interactive Swagger UI to test each endpoint

## Metrics

Prometheus metrics exposed at `http://localhost:9090/actuator/prometheus`:
- `mcp.trip.service` - Trip request timing
- `mcp.geocoder.service` - Geocoder request timing
