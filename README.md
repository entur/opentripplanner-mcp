# OpenTripPlanner MCP Server

This is a Model Context Protocol (MCP) server for OpenTripPlanner's transmodel GraphQL API, implemented in Go using the [go-mcp framework](https://github.com/mark3labs/mcp-go). It allows AI agents to use the OpenTripPlanner service as a tool for trip planning in Norwegian/Nordic public transport.

## What is MCP?

The [Model Context Protocol (MCP)](https://modelcontextprotocol.io) is a standardized protocol designed to allow AI agents to interact with tools and services. It provides a consistent interface for LLMs to access external functionality, similar to how REST APIs work for web services but specifically optimized for AI agent interactions.

This server implements the MCP specification to provide a standardized interface for AI agents to access trip planning functionality from OpenTripPlanner.

## Features

- Trip planning between two points or coordinate pairs in Norwegian/Nordic public transport
- Integration with geocoder for location search
- Implements MCP specification for AI agent integration
- High-performance Go implementation using go-mcp framework
- Kubernetes-ready with included deployment manifests

## Tools Provided

This MCP server exposes two main tools:

1. **trip** - Find trip options between two locations
   - Parameters:
     - `from`: Starting location (address, place name, or coordinates)
     - `to`: Destination location (address, place name, or coordinates)
     - `departureTime`: Optional departure time in ISO format
     - `arrivalTime`: Optional arrival time in ISO format
     - `maxResults`: Maximum number of trip options to return (default: 3)

2. **geocode** - Search for locations by name or address
   - Parameters:
     - `text`: Location text to search for
     - `maxResults`: Maximum number of results to return (default: 5)

## API Endpoints

- Development API: https://api.dev.entur.io/journey-planner/v3/graphql
- Production API: https://api.entur.io/journey-planner/v3/graphql
- Geocoder API: https://api.dev.entur.io/geocoder/v1/autocomplete?text={query}

## Getting Started

### Prerequisites

- Docker (for building and running)
- Go 1.23 or higher (only if you want to build directly without Docker)

### Installation and Running

The project uses the go-mcp framework which requires Go 1.23. To simplify development and deployment, we provide Docker-based scripts that handle this requirement:

```bash
# For development environment
./run-dev.sh

# For production environment
./run-prod.sh
```

### Building Directly (requires Go 1.23+)

If you have Go 1.23 or higher installed, you can build and run directly:

```bash
go mod download
go run main.go
```

Or build a binary:

```bash
go build -o opentripplanner-mcp
```

## Deployment

This server can be deployed as a Kubernetes service. A Dockerfile and Kubernetes manifest are included for containerization.

### Docker Build

```bash
docker build -t opentripplanner-mcp:latest .
```

### Kubernetes Deployment

```bash
kubectl apply -f kubernetes.yaml
```

## Configuration

Environment variables:

- `ENV`: Environment to run the server in (development/production)
- `API_URL`: URL of the OpenTripPlanner API (defaults to development or production based on ENV)
- `CLIENT_NAME`: Client name to use in API requests (default: "entur-mcp")

## Using with AI Agents

### Claude Code

To use this MCP server with Claude Code:

1. **Build the binary** (if not already built):
   ```bash
   go build -o opentripplanner-mcp
   ```

2. **Add the MCP server** using the Claude Code CLI:
   ```bash
   claude mcp add opentripplanner /path/to/opentripplanner-mcp/opentripplanner-mcp --env ENV=development
   ```

   Replace `/path/to/opentripplanner-mcp/` with the actual absolute path to your binary.

3. **Enable the server** in your Claude Code session:
   - Type `/mcp` in Claude Code to manage MCP servers
   - The server should connect and show as available

4. **Use the tools** in your conversation:
   - Ask Claude to plan a trip: "Plan a trip from Oslo to Bergen"
   - Ask Claude to geocode a location: "Find the coordinates for Oslo Central Station"

The server provides two tools:
- `trip`: Find trip options between two locations with public transport
- `geocode`: Search for locations by name or address

### Other MCP Clients

AI agents that support the MCP protocol can use this server to access trip planning functionality. The server communicates via stdio transport and provides:

1. Tool discovery through the `tools/list` method
2. Tool execution through the `tools/call` method

For example, an AI agent could use the `trip` tool to find travel options between Oslo and Bergen, or use the `geocode` tool to find coordinates for a specific address.
