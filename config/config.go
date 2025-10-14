package config

import (
	"os"
	"strconv"
)

// Config holds the application configuration
type Config struct {
	Port        int
	Environment string
	APIURL      string
	GeocoderURL string
	ClientName  string
}

// LoadConfig loads configuration from environment variables
func LoadConfig() *Config {
	// Default values
	config := &Config{
		Port:        3000,
		Environment: "development",
		APIURL:      "",
		GeocoderURL: "https://api.dev.entur.io/geocoder/v1/autocomplete",
		ClientName:  "entur-mcp",
	}

	// Override with environment variables if present
	if port := os.Getenv("PORT"); port != "" {
		if p, err := strconv.Atoi(port); err == nil {
			config.Port = p
		}
	}

	if env := os.Getenv("ENV"); env != "" {
		config.Environment = env
	}

	// Set API URL based on environment if not explicitly provided
	if apiURL := os.Getenv("API_URL"); apiURL != "" {
		config.APIURL = apiURL
	} else if config.Environment == "production" {
		config.APIURL = "https://api.entur.io/journey-planner/v3/graphql"
	} else {
		config.APIURL = "https://api.dev.entur.io/journey-planner/v3/graphql"
	}

	if clientName := os.Getenv("CLIENT_NAME"); clientName != "" {
		config.ClientName = clientName
	}

	return config
}
