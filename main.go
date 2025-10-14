package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"strings"
	"bufio"

	"github.com/mark3labs/mcp-go/mcp"
	"github.com/mark3labs/mcp-go/server"
)

// Configuration constants
const (
	DefaultClientName = "entur-mcp"
)

// Environment variables
var (
	Environment = getEnv("ENV", "development")
	APIURL      string
	geocoderURL string
	ClientName  = getEnv("CLIENT_NAME", DefaultClientName)
)

func main() {
	// Configure logging to stderr
	log.SetOutput(os.Stderr)

	// Initialize API URLs based on environment
	if Environment == "production" {
		APIURL = "https://api.entur.io/journey-planner/v3/graphql"
		geocoderURL = "https://api.entur.io/geocoder/v1/autocomplete"
	} else {
		APIURL = "https://api.dev.entur.io/journey-planner/v3/graphql"
		geocoderURL = "https://api.dev.entur.io/geocoder/v1/autocomplete"
	}

	log.Printf("Initializing OpenTripPlanner MCP server (Environment: %s)", Environment)

	// Create MCP server
	s := server.NewMCPServer(
		"OpenTripPlanner",
		"1.0.0",
		server.WithToolCapabilities(false),
		server.WithRecovery(),
	)

	// Create trip tool
	tripTool := mcp.NewTool("trip",
		mcp.WithDescription("Find trip options between two locations using Norwegian/Nordic public transport"),
		mcp.WithString("from", mcp.Required(), mcp.Description("Starting location (address, place name, or coordinates)")),
		mcp.WithString("to", mcp.Required(), mcp.Description("Destination location (address, place name, or coordinates)")),
		mcp.WithString("departureTime", mcp.Description("Departure time in ISO format (e.g., 2023-05-26T12:00:00)")),
		mcp.WithString("arrivalTime", mcp.Description("Arrival time in ISO format (e.g., 2023-05-26T14:00:00)")),
		mcp.WithNumber("maxResults", mcp.Description("Maximum number of trip options to return")),
	)

	// Create geocode tool
	geocodeTool := mcp.NewTool("geocode",
		mcp.WithDescription("Search for locations by name or address"),
		mcp.WithString("text", mcp.Required(), mcp.Description("Location text to search for")),
		mcp.WithNumber("maxResults", mcp.Description("Maximum number of results to return")),
	)

	// Register trip tool handler
	s.AddTool(tripTool, func(ctx context.Context, request mcp.CallToolRequest) (*mcp.CallToolResult, error) {
		// Extract parameters
		from, err := request.RequireString("from")
		if err != nil {
			return mcp.NewToolResultError(err.Error()), nil
		}

		to, err := request.RequireString("to")
		if err != nil {
			return mcp.NewToolResultError(err.Error()), nil
		}

		// Get optional parameters
		args := request.GetArguments()
		departureTime := ""
		if dt, ok := args["departureTime"].(string); ok {
			departureTime = dt
		}

		arrivalTime := ""
		if at, ok := args["arrivalTime"].(string); ok {
			arrivalTime = at
		}

		maxResults := 3
		if mr, ok := args["maxResults"].(float64); ok && mr > 0 {
			maxResults = int(mr)
		}

		// Process trip request
		result, err := handleTripRequest(from, to, departureTime, arrivalTime, maxResults, APIURL, geocoderURL)
		if err != nil {
			return mcp.NewToolResultError(fmt.Sprintf("Error planning trip: %v", err)), nil
		}

		// Format the result as JSON string
		resultJSON, err := json.Marshal(result)
		if err != nil {
			return mcp.NewToolResultError(fmt.Sprintf("Error marshaling result: %v", err)), nil
		}
		return mcp.NewToolResultText(string(resultJSON)), nil
	})

	// Register geocode tool handler
	s.AddTool(geocodeTool, func(ctx context.Context, request mcp.CallToolRequest) (*mcp.CallToolResult, error) {
		// Extract parameters
		text, err := request.RequireString("text")
		if err != nil {
			return mcp.NewToolResultError(err.Error()), nil
		}

		// Get optional parameters
		maxResults := 5
		if mr, ok := request.GetArguments()["maxResults"].(float64); ok && mr > 0 {
			maxResults = int(mr)
		}

		// Process geocode request
		result, err := handleGeocodeRequest(text, maxResults, geocoderURL)
		if err != nil {
			return mcp.NewToolResultError(fmt.Sprintf("Error geocoding: %v", err)), nil
		}

		// Format the result as JSON string
		resultJSON, err := json.Marshal(result)
		if err != nil {
			return mcp.NewToolResultError(fmt.Sprintf("Error marshaling result: %v", err)), nil
		}
		return mcp.NewToolResultText(string(resultJSON)), nil
	})

	log.Println("Server configured with tools: trip, geocode")
	log.Println("Starting OpenTripPlanner MCP server using stdio transport")

	// Handle the JSON-RPC communication directly
	handleJSONRPC(s)

	log.Println("Server shutdown complete")
}



// Helper function to get environment variables with defaults
func getEnv(key, defaultValue string) string {
	value := os.Getenv(key)
	if value == "" {
		return defaultValue
	}
	return value
}

// handleJSONRPC handles JSON-RPC communication directly
func handleJSONRPC(s *server.MCPServer) {
	reader := bufio.NewReader(os.Stdin)
	writer := os.Stdout

	log.Println("Ready to receive JSON-RPC requests")

	for {
		// Read a line from stdin
		line, err := reader.ReadString('\n')
		if err != nil {
			log.Printf("Error reading from stdin: %v", err)
			break
		}

		log.Printf("Received request: %s", line)

		// Parse the JSON-RPC request
		var request map[string]interface{}
		if err := json.Unmarshal([]byte(line), &request); err != nil {
			log.Printf("Error parsing JSON-RPC request: %v", err)
			continue
		}

		// Check if it's an initialize request
		method, ok := request["method"].(string)
		if !ok {
			log.Printf("Invalid JSON-RPC request: missing method")
			continue
		}

		// Handle notifications (which don't have an id)
		if strings.HasPrefix(method, "notifications/") {
			log.Printf("Received notification: %s", method)
			continue
		}

		id, ok := request["id"]
		if !ok {
			log.Printf("Invalid JSON-RPC request: missing id for method %s", method)
			continue
		}

		if method == "initialize" {
			// Handle initialize request directly
			response := map[string]interface{}{
				"jsonrpc": "2.0",
				"id": id,
				"result": map[string]interface{}{
					"protocolVersion": "2024-11-05",
					"capabilities": map[string]interface{}{
						"tools": map[string]interface{}{},
					},
					"serverInfo": map[string]interface{}{
						"name": "OpenTripPlanner",
						"version": "1.0.0",
					},
				},
			}

			// Send the response
			responseJSON, _ := json.Marshal(response)
			fmt.Fprintf(writer, "%s\n", responseJSON)
			log.Printf("Sent initialize response: %s", responseJSON)
		} else if method == "tools/list" {
			// Handle tools/list request
			response := map[string]interface{}{
				"jsonrpc": "2.0",
				"id": id,
				"result": map[string]interface{}{
					"tools": []map[string]interface{}{
						{
							"name": "trip",
							"description": "Find trip options between two locations using Norwegian/Nordic public transport",
							"inputSchema": map[string]interface{}{
								"type": "object",
								"properties": map[string]interface{}{
									"from": map[string]interface{}{
										"type": "string",
										"description": "Starting location (address, place name, or coordinates)",
									},
									"to": map[string]interface{}{
										"type": "string",
										"description": "Destination location (address, place name, or coordinates)",
									},
									"departureTime": map[string]interface{}{
										"type": "string",
										"description": "Departure time in ISO format (e.g., 2023-05-26T12:00:00)",
									},
									"arrivalTime": map[string]interface{}{
										"type": "string",
										"description": "Arrival time in ISO format (e.g., 2023-05-26T14:00:00)",
									},
									"maxResults": map[string]interface{}{
										"type": "number",
										"description": "Maximum number of trip options to return",
									},
								},
								"required": []string{"from", "to"},
							},
						},
						{
							"name": "geocode",
							"description": "Search for locations by name or address",
							"inputSchema": map[string]interface{}{
								"type": "object",
								"properties": map[string]interface{}{
									"text": map[string]interface{}{
										"type": "string",
										"description": "Location text to search for",
									},
									"maxResults": map[string]interface{}{
										"type": "number",
										"description": "Maximum number of results to return",
									},
								},
								"required": []string{"text"},
							},
						},
					},
				},
			}

			// Send the response
			responseJSON, _ := json.Marshal(response)
			fmt.Fprintf(writer, "%s\n", responseJSON)
			log.Printf("Sent tools/list response: %s", responseJSON)
		} else if method == "tools/call" {
			// Handle tool calls
			params, ok := request["params"].(map[string]interface{})
			if !ok {
				log.Printf("Invalid tools/call request: missing params")
				continue
			}

			toolName, ok := params["name"].(string)
			if !ok {
				log.Printf("Invalid tools/call request: missing tool name")
				continue
			}

			arguments, _ := params["arguments"].(map[string]interface{})

			log.Printf("Calling tool '%s' with arguments: %v", toolName, arguments)

			var resultText string
			var callErr error

			// Call the appropriate tool handler
			if toolName == "trip" {
				from, _ := arguments["from"].(string)
				to, _ := arguments["to"].(string)
				departureTime, _ := arguments["departureTime"].(string)
				arrivalTime, _ := arguments["arrivalTime"].(string)
				maxResults := 3
				if mr, ok := arguments["maxResults"].(float64); ok && mr > 0 {
					maxResults = int(mr)
				}

				result, err := handleTripRequest(from, to, departureTime, arrivalTime, maxResults, APIURL, geocoderURL)
				if err != nil {
					callErr = err
				} else {
					resultJSON, _ := json.Marshal(result)
					resultText = string(resultJSON)
				}
			} else if toolName == "geocode" {
				text, _ := arguments["text"].(string)
				maxResults := 5
				if mr, ok := arguments["maxResults"].(float64); ok && mr > 0 {
					maxResults = int(mr)
				}

				result, err := handleGeocodeRequest(text, maxResults, geocoderURL)
				if err != nil {
					callErr = err
				} else {
					resultJSON, _ := json.Marshal(result)
					resultText = string(resultJSON)
				}
			} else {
				callErr = fmt.Errorf("unknown tool: %s", toolName)
			}

			// Build response
			var response map[string]interface{}
			if callErr != nil {
				response = map[string]interface{}{
					"jsonrpc": "2.0",
					"id": id,
					"result": map[string]interface{}{
						"content": []map[string]interface{}{
							{
								"type": "text",
								"text": fmt.Sprintf("Error: %v", callErr),
							},
						},
						"isError": true,
					},
				}
			} else {
				response = map[string]interface{}{
					"jsonrpc": "2.0",
					"id": id,
					"result": map[string]interface{}{
						"content": []map[string]interface{}{
							{
								"type": "text",
								"text": resultText,
							},
						},
					},
				}
			}

			// Send the response
			responseJSON, _ := json.Marshal(response)
			fmt.Fprintf(writer, "%s\n", responseJSON)
			log.Printf("Sent tools/call response")
		} else {
			// For other requests, log and send a simple error response
			log.Printf("Unsupported method: %s", method)
			response := map[string]interface{}{
				"jsonrpc": "2.0",
				"id": id,
				"error": map[string]interface{}{
					"code": -32601,
					"message": "Method not found",
				},
			}

			// Send the response
			responseJSON, _ := json.Marshal(response)
			fmt.Fprintf(writer, "%s\n", responseJSON)
			log.Printf("Sent error response: %s", responseJSON)
		}
	}
}

func getAPIURL(env string) string {
	if env == "production" {
		return "https://api.entur.io/journey-planner/v3/graphql"
	} else {
		return "https://api.dev.entur.io/journey-planner/v3/graphql"
	}
}

// handleGeocodeRequest processes a geocode request
func handleGeocodeRequest(text string, maxResults int, geocoderURL string) (interface{}, error) {
	log.Printf("Geocoding '%s'", text)
	// Create the geocoder URL with parameters
	geocodeURL := fmt.Sprintf("%s?text=%s", geocoderURL, url.QueryEscape(text))

	// Make the request
	req, err := http.NewRequest("GET", geocodeURL, nil)
	if err != nil {
		return nil, fmt.Errorf("error creating request: %w", err)
	}

	// Add headers
	req.Header.Add("ET-Client-Name", ClientName)

	// Send the request
	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("error making request: %w", err)
	}
	defer resp.Body.Close()

	// Read the response
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("error reading response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("geocoder API returned status %d: %s", resp.StatusCode, string(body))
	}

	// Parse the response
	var result map[string]interface{}
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("error parsing response: %w", err)
	}

	// Limit the number of features if needed
	if features, ok := result["features"].([]interface{}); ok && len(features) > maxResults {
		result["features"] = features[:maxResults]
	}

	return result, nil
}

// handleTripRequest processes a trip planning request
func handleTripRequest(from, to, departureTime, arrivalTime string, maxResults int, apiURL, geocoderURL string) (interface{}, error) {
	log.Printf("Planning trip from '%s' to '%s'", from, to)
	// First, geocode the from and to locations if they're not coordinates
	fromLocation, err := geocodeIfNeeded(from, geocoderURL)
	if err != nil {
		return nil, fmt.Errorf("error geocoding 'from' location: %w", err)
	}

	toLocation, err := geocodeIfNeeded(to, geocoderURL)
	if err != nil {
		return nil, fmt.Errorf("error geocoding 'to' location: %w", err)
	}

	// Construct GraphQL query
	dateTimeParam := ""
	if departureTime != "" {
		dateTimeParam = fmt.Sprintf(`dateTime: "%s"`, departureTime)
	} else if arrivalTime != "" {
		dateTimeParam = fmt.Sprintf(`arriveBy: true dateTime: "%s"`, arrivalTime)
	}

	query := fmt.Sprintf(`{
		trip(
			from: {
				place: "%s"
				coordinates: {
					latitude: %f
					longitude: %f
				}
			}
			to: {
				place: "%s"
				coordinates: {
					latitude: %f
					longitude: %f
				}
			}
			%s
			numTripPatterns: %d
		) {
			tripPatterns {
				duration
				startTime
				endTime
				legs {
					mode
					distance
					duration
					fromPlace {
						name
					}
					toPlace {
						name
					}
					%s
				}
			}
		}
	}`,
		fromLocation.Place, fromLocation.Latitude, fromLocation.Longitude,
		toLocation.Place, toLocation.Latitude, toLocation.Longitude,
		dateTimeParam, maxResults, getTransitLegFields())

	// Make the GraphQL request
	reqBody := map[string]string{"query": query}
	reqJSON, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("error marshaling request: %w", err)
	}

	req, err := http.NewRequest("POST", apiURL, strings.NewReader(string(reqJSON)))
	if err != nil {
		return nil, fmt.Errorf("error creating request: %w", err)
	}

	// Add headers
	req.Header.Add("Content-Type", "application/json")
	req.Header.Add("ET-Client-Name", ClientName)

	// Send the request
	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("error making request: %w", err)
	}
	defer resp.Body.Close()

	// Read the response
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("error reading response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("GraphQL API returned status %d: %s", resp.StatusCode, string(body))
	}

	// Parse the response
	var result map[string]interface{}
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("error parsing response: %w", err)
	}

	// Check for GraphQL errors
	if errors, ok := result["errors"].([]interface{}); ok && len(errors) > 0 {
		return nil, fmt.Errorf("GraphQL query error: %v", errors)
	}

	return result["data"], nil
}

// Location represents a geocoded location
type Location struct {
	Place     string
	Latitude  float64
	Longitude float64
}

// geocodeIfNeeded geocodes a location string if it's not already coordinates
func geocodeIfNeeded(location string, geocoderURL string) (*Location, error) {
	// Check if the location is already in coordinate format (e.g., "59.909,10.746")
	coords := strings.Split(location, ",")
	if len(coords) == 2 {
		// Try to parse as coordinates
		var lat, lng float64
		_, err1 := fmt.Sscanf(coords[0], "%f", &lat)
		_, err2 := fmt.Sscanf(coords[1], "%f", &lng)

		if err1 == nil && err2 == nil {
			return &Location{
				Place:     "coordinate",
				Latitude:  lat,
				Longitude: lng,
			}, nil
		}
	}

	// Otherwise, geocode the location
	result, err := handleGeocodeRequest(location, 1, geocoderURL)
	if err != nil {
		return nil, err
	}

	// Extract the first feature
	resultMap, ok := result.(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("unexpected geocode result format")
	}

	features, ok := resultMap["features"].([]interface{})
	if !ok || len(features) == 0 {
		return nil, fmt.Errorf("no locations found for: %s", location)
	}

	feature, ok := features[0].(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("unexpected feature format")
	}

	geometry, ok := feature["geometry"].(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("missing geometry in feature")
	}

	coordinates, ok := geometry["coordinates"].([]interface{})
	if !ok || len(coordinates) < 2 {
		return nil, fmt.Errorf("invalid coordinates in feature")
	}

	lng, ok1 := coordinates[0].(float64)
	lat, ok2 := coordinates[1].(float64)
	if !ok1 || !ok2 {
		return nil, fmt.Errorf("coordinates are not numbers")
	}

	properties, ok := feature["properties"].(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("missing properties in feature")
	}

	name, ok := properties["name"].(string)
	if !ok || name == "" {
		name = "location"
	}

	return &Location{
		Place:     name,
		Latitude:  lat,
		Longitude: lng,
	}, nil
}

// getTransitLegFields returns the GraphQL fields for transit legs
func getTransitLegFields() string {
	return `line {
						publicCode
						name
					}
					aimedStartTime
					expectedStartTime
					aimedEndTime
					expectedEndTime`
}
