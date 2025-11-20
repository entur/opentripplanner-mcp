package org.entur.mcp.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import org.entur.mcp.exception.GeocodingException;
import org.entur.mcp.model.Location;
import org.entur.mcp.validation.InputValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
@Timed(value = "mcp.geocoder.service", description = "Requests towards the Geocoder-service")
public class GeocoderService {

    private static final Logger log = LoggerFactory.getLogger(GeocoderService.class);

    private final HttpClient client;

    private final String geocodeURL;

    private final String etClientName;

    public GeocoderService(
            @Value("${org.entur.geocoder.url}") String geocodeURL,
            @Value("${org.entur.mcp.client_name:entur-mcp}") String etClientName) {
        this.geocodeURL = geocodeURL;
        this.etClientName = etClientName;
        client = HttpClient.newHttpClient();
    }

    public Map<String, Object> handleGeocodeRequest(String text, int maxResults) {
        // Validate input
        InputValidator.validateLocation(text, "text");
        int validatedMaxResults = InputValidator.validateAndNormalizeMaxResults(maxResults, 10);

        log.info("Geocoding location: '{}' (maxResults: {})", text, validatedMaxResults);

        // Create the geocoder URL with parameters
        String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String requestUrl = String.format("%s?text=%s", geocodeURL, encodedText);

        // Make the request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .header("ET-Client-Name", etClientName)
                .GET()
                .build();

        // Send the request
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.error("IO error during geocoding request for '{}': {}", text, e.getMessage());
            throw new GeocodingException(text, "Network error while geocoding location", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Geocoding request interrupted for '{}'", text);
            throw new GeocodingException(text, "Geocoding request was interrupted", e);
        }

        // Check status code
        if (response.statusCode() != 200) {
            log.error("Geocoder API returned status {} for '{}': {}",
                response.statusCode(), text, response.body());
            throw new GeocodingException(text,
                String.format("Geocoder API returned status %d", response.statusCode()));
        }

        // Parse the response
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> result;
        try {
            result = objectMapper.readValue(response.body(),
                    new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("Error parsing geocoder response for '{}': {}", text, e.getMessage());
            throw new GeocodingException(text, "Invalid response format from geocoder API", e);
        }

        // Limit the number of features if needed
        if (result.containsKey("features")) {
            Object featuresObj = result.get("features");
            if (featuresObj instanceof List) {
                List<Object> features = (List<Object>) featuresObj;
                if (features.size() > validatedMaxResults) {
                    result.put("features", features.subList(0, validatedMaxResults));
                }
            }
        }

        log.debug("Successfully geocoded '{}', found {} features", text,
            result.containsKey("features") ? ((List<?>) result.get("features")).size() : 0);

        return result;
    }

    public Location geocodeIfNeeded(String location) {
        // Validate input
        InputValidator.validateLocation(location, "location");

        // Check if the location is already in coordinate format (e.g., "59.909,10.746")
        String[] coords = location.split(",");
        if (coords.length == 2) {
            // Try to parse as coordinates
            try {
                double lat = Double.parseDouble(coords[0].trim());
                double lng = Double.parseDouble(coords[1].trim());

                log.debug("Using provided coordinates: lat={}, lng={}", lat, lng);
                return new Location("coordinate", lat, lng);
            } catch (NumberFormatException e) {
                log.debug("Failed to parse as coordinates, will geocode: {}", location);
                // Not valid coordinates, continue to geocoding
            }
        }

        // Otherwise, geocode the location
        log.debug("Geocoding location to get coordinates: {}", location);
        Map<String, Object> feature = getFeature(location);

        // Extract geometry
        if (!feature.containsKey("geometry")) {
            throw new GeocodingException(location, "Missing geometry in feature");
        }

        Object geometryObj = feature.get("geometry");
        if (!(geometryObj instanceof Map)) {
            throw new GeocodingException(location, "Geometry is not a map");
        }

        Map<String, Object> geometry = (Map<String, Object>) geometryObj;

        if (!geometry.containsKey("coordinates")) {
            throw new GeocodingException(location, "Missing coordinates in geometry");
        }

        Object coordinatesObj = geometry.get("coordinates");
        if (!(coordinatesObj instanceof List)) {
            throw new GeocodingException(location, "Coordinates is not a list");
        }

        List<Object> coordinates = (List<Object>) coordinatesObj;

        if (coordinates.size() < 2) {
            throw new GeocodingException(location, "Invalid coordinates in feature");
        }

        // Extract longitude and latitude (GeoJSON format: [lng, lat])
        double lng;
        double lat;

        try {
            // Handle both Double and Integer types
            Object lngObj = coordinates.get(0);
            Object latObj = coordinates.get(1);

            lng = (lngObj instanceof Number) ? ((Number) lngObj).doubleValue() : 0.0;
            lat = (latObj instanceof Number) ? ((Number) latObj).doubleValue() : 0.0;
        } catch (Exception e) {
            throw new GeocodingException(location, "Coordinates are not numbers", e);
        }

        // Extract properties
        if (!feature.containsKey("properties")) {
            throw new GeocodingException(location, "Missing properties in feature");
        }

        Object propertiesObj = feature.get("properties");
        if (!(propertiesObj instanceof Map)) {
            throw new GeocodingException(location, "Properties is not a map");
        }

        Map<String, Object> properties = (Map<String, Object>) propertiesObj;

        String name = "location";
        if (properties.containsKey("name")) {
            Object nameObj = properties.get("name");
            if (nameObj instanceof String && !((String) nameObj).isEmpty()) {
                name = (String) nameObj;
            }
        }

        log.info("Successfully geocoded '{}' to '{}' (lat={}, lng={})", location, name, lat, lng);
        return new Location(name, lat, lng);
    }

    private Map<String, Object> getFeature(String location) {

        Map<String, Object> result = handleGeocodeRequest(location, 1);

        if (result == null) {
            throw new GeocodingException(location, "Geocode request returned null");
        }

        if (!result.containsKey("features")) {
            throw new GeocodingException(location, "No features in geocode result");
        }

        Object featuresObj = result.get("features");
        if (!(featuresObj instanceof List)) {
            throw new GeocodingException(location, "Features is not a list");
        }

        List<Object> features = (List<Object>) featuresObj;

        if (features.isEmpty()) {
            throw new GeocodingException(location,
                String.format("No locations found for: %s. Please check spelling or try a different search term.", location));
        }

        Object featureObj = features.getFirst();
        if (!(featureObj instanceof Map)) {
            throw new GeocodingException(location, "Unexpected feature format");
        }

        return (Map<String, Object>) featureObj;
    }
}
