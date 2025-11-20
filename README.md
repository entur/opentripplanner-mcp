# OpenTripPlanner MCP Server

This is a Model Context Protocol (MCP) server for OpenTripPlanner's transmodel GraphQL API, implemented in Java using Spring Boot and [Spring AI's MCP framework](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html#_spring_ai_mcp_integration). It allows AI agents to use the OpenTripPlanner service as a tool for trip planning in Norwegian/Nordic public transport.

## What is MCP?

The [Model Context Protocol (MCP)](https://modelcontextprotocol.io) is a standardized protocol designed to allow AI agents to interact with tools and services. It provides a consistent interface for LLMs to access external functionality, similar to how REST APIs work for web services but specifically optimized for AI agent interactions.

This server implements the MCP specification to provide a standardized interface for AI agents to access trip planning functionality from OpenTripPlanner.

## Features

- Trip planning between two points or coordinate pairs in Norwegian/Nordic public transport
- Integration with Entur's geocoder for location search
- Implements MCP specification for AI agent integration using Spring AI annotations
- HTTP-based MCP server (port 8080)
- Comprehensive test suite with unit and integration tests
- Docker-ready with included Dockerfile

## Tools Provided

This MCP server exposes two main tools:

1. **trip** - Find trip options between two locations
   - Parameters:
     - `from`: Starting location (address, place name, or coordinates in "lat,lng" format)
     - `to`: Destination location (address, place name, or coordinates in "lat,lng" format)
     - `departureTime`: Optional departure time in ISO format (e.g., "2023-05-26T12:00:00")
     - `arrivalTime`: Optional arrival time in ISO format (e.g., "2023-05-26T14:00:00")
     - `maxResults`: Maximum number of trip options to return (default: 3)

2. **geocode** - Search for locations by name or address
   - Parameters:
     - `text`: Location text to search for
     - `maxResults`: Maximum number of results to return (default: 10)

## API Endpoints

The server integrates with Entur's public APIs:

- **Development** (default):
  - Journey Planner: https://api.dev.entur.io/journey-planner/v3/graphql
  - Geocoder: https://api.dev.entur.io/geocoder/v2/autocomplete

- **Production**:
  - Journey Planner: https://api.entur.io/journey-planner/v3/graphql
  - Geocoder: https://api.entur.io/geocoder/v2/autocomplete

## Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.6 or higher
- Docker (optional, for containerized deployment)

### Building and Running

**Build the project**:
```bash
mvn clean package
```

**Run the application**:
```bash
mvn spring-boot:run
```

The server will start on port 8080 by default.

**Run with production API endpoints**:
```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=prod
```

### Running Tests

**Run all tests**:
```bash
mvn test
```

**Run only unit tests** (excludes integration tests):
```bash
mvn test -Dtest=*Test -Dtest=!*IntegrationTest
```

**Run only integration tests** (requires network access):
```bash
mvn test -Dtest=*IntegrationTest
```

**Run a specific test class**:
```bash
mvn test -Dtest=TripSearchToolTest
```

## Docker Deployment

### Building the Docker Image

First, build the application JAR:
```bash
mvn clean package
```

Then build the Docker image:
```bash
docker build -t opentripplanner-mcp:latest .
```

### Running the Container

```bash
docker run -p 8080:8080 opentripplanner-mcp:latest
```

The MCP server will be available at `http://localhost:8080/mcp`.

## Configuration

The application can be configured through `src/main/resources/application.properties` or via environment variables:

- `SERVER_PORT`: HTTP port to listen on (default: 8080)
- `ORG_ENTUR_OTP_URL`: OpenTripPlanner GraphQL endpoint URL
- `ORG_ENTUR_GEOCODER_URL`: Entur Geocoder REST API URL
- `ORG_ENTUR_MCP_CLIENT_NAME`: Client identifier for API requests (default: "entur-mcp")

Example with environment variables:
```bash
export SERVER_PORT=8080
export ORG_ENTUR_OTP_URL=https://api.entur.io/journey-planner/v3/graphql
export ORG_ENTUR_GEOCODER_URL=https://api.entur.io/geocoder/v2/autocomplete
mvn spring-boot:run
```

## Using with AI Agents

### Claude Desktop

To use this MCP server with Claude Desktop, you need to run it as an HTTP server and configure Claude Desktop to connect to it.

1. **Start the MCP server**:
   ```bash
   mvn spring-boot:run
   ```

2. **Configure Claude Desktop** to connect to `http://localhost:8080/mcp` as an MCP server endpoint.

3. **Use the tools** in your conversation:
   - Ask Claude to plan a trip: "Plan a trip from Oslo to Bergen"
   - Ask Claude to geocode a location: "Find the coordinates for Oslo Central Station"

### Other MCP Clients

AI agents that support the MCP protocol over HTTP can connect to this server at `http://localhost:8080/mcp`. The server provides:

1. Tool discovery through the MCP `tools/list` method
2. Tool execution through the MCP `tools/call` method

The server uses Spring AI's MCP annotations framework, which automatically handles the JSON-RPC protocol implementation.

## Architecture

The application follows standard Spring Boot layered architecture:

- **App.java**: Main Spring Boot application entry point
- **tools/TripSearchTool.java**: MCP tool definitions using `@McpTool` annotations
- **services/**: Business logic layer
  - `OtpSearchService.java`: Trip planning via GraphQL
  - `GeocoderService.java`: Location geocoding via REST
- **model/**: Domain models (Location, ErrorResponse)
- **validation/**: Input validation logic
- **exception/**: Custom exception classes

The server uses:
- **Spring Boot 3.5.7** for the application framework
- **Spring AI 1.1.0** for MCP server implementation
- **Jackson** for JSON processing
- **Java HTTP Client** for external API calls
- **JUnit 5 & AssertJ** for testing
- **OkHttp MockWebServer** for HTTP mocking in tests

## Development

### Project Structure

```
src/
├── main/
│   ├── java/org/entur/mcp/
│   │   ├── App.java                    # Main application
│   │   ├── tools/                      # MCP tool definitions
│   │   ├── services/                   # Business logic
│   │   ├── model/                      # Domain models
│   │   ├── validation/                 # Input validation
│   │   └── exception/                  # Custom exceptions
│   └── resources/
│       └── application.properties      # Configuration
└── test/
    └── java/org/entur/mcp/            # Test classes
```

### Adding New Tools

To add a new MCP tool:

1. Create a method in `TripSearchTool.java` or a new `@Component` class
2. Annotate it with `@McpTool` and specify name and description
3. Add parameters with `@McpToolParam` annotations
4. Return a JSON string response
5. Handle errors appropriately and return structured error responses

Example:
```java
@McpTool(
    name = "my_tool",
    description = "Description of what this tool does"
)
public String myTool(
    @McpToolParam(description = "Parameter description", required = true) String param
) {
    // Implementation
    return objectMapper.writeValueAsString(result);
}
```

## Contributing

This is a project by Entur for integrating OpenTripPlanner with AI agents through the Model Context Protocol.

## License

Please check with Entur for licensing information.
