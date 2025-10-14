#!/bin/bash

# Build and run the OpenTripPlanner MCP server in development mode
# This script uses Docker to handle the Go 1.23 requirement for the mcp-go framework

echo "Building OpenTripPlanner MCP server development image with Go 1.23..."
docker build --target development -t opentripplanner-mcp-dev .

echo "\nRunning OpenTripPlanner MCP server in development mode..."
echo "Press Ctrl+C to stop the server\n"

docker run -it --rm \
  -e ENV=development \
  -e CLIENT_NAME=entur-mcp \
  opentripplanner-mcp-dev
