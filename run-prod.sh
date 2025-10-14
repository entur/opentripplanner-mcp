#!/bin/bash

# Build and run the OpenTripPlanner MCP server in production mode
# This script uses Docker to handle the Go 1.23 requirement for the mcp-go framework

echo "Building OpenTripPlanner MCP server production image with Go 1.23..."
docker build --target production -t opentripplanner-mcp-prod .

echo "\nRunning OpenTripPlanner MCP server in production mode..."
echo "Press Ctrl+C to stop the server\n"

docker run -it --rm \
  -e ENV=production \
  -e CLIENT_NAME=entur-mcp \
  opentripplanner-mcp-prod
