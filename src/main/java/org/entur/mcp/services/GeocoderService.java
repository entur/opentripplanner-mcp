package org.entur.mcp.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import org.entur.mcp.exception.GeocodingException;
import org.entur.mcp.metrics.MetricsUtils;
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
import java.util.Optional;

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
                .header(MetricsUtils.ET_CLIENT_NAME_HEADER, etClientName)
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

        // Try to parse as coordinates first
        Optional<Location> coordinateLocation = parseAsCoordinates(location);
        if (coordinateLocation.isPresent()) {
            return coordinateLocation.get();
        }

        // Otherwise, geocode the location
        log.debug("Geocoding location to get coordinates: {}", location);
        Map<String, Object> feature = getFeature(location);

        // Extract geometry and coordinates using type-safe helpers
        Map<String, Object> geometry = extractMap(feature, "geometry", location, "feature");
        List<Object> coordinates = extractList(geometry, "coordinates", location, "geometry");
        double[] latLng = extractLatLng(coordinates, location);
        double lat = latLng[0];
        double lng = latLng[1];

        // Extract properties using type-safe helper
        Map<String, Object> properties = extractMap(feature, "properties", location, "feature");

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

    /**
     * Resolves a stop identifier to an NSR:StopPlace ID.
     * If input is already an NSR ID, returns it directly.
     * Otherwise, geocodes the input and extracts the ID from the first result.
     */
    public String resolveStopId(String input) {
        InputValidator.validateLocation(input, "stop");

        // If already an NSR ID, return as-is
        if (input.startsWith("NSR:StopPlace:") || input.startsWith("NSR:Quay:")) {
            log.debug("Input is already an NSR ID: {}", input);
            return input;
        }

        // Geocode and extract ID from first result
        log.debug("Resolving stop name to ID: {}", input);
        Map<String, Object> feature = getFeature(input);

        // Extract properties.id
        Map<String, Object> properties = (Map<String, Object>) feature.get("properties");
        Object idObj = properties.get("id");

        if (!(idObj instanceof String stopId)) {
            throw new GeocodingException(input, "No stop ID found for location");
        }

        log.info("Resolved '{}' to stop ID: {}", input, stopId);
        return stopId;
    }

    private Map<String, Object> getFeature(String location) {

        Map<String, Object> result = handleGeocodeRequest(location, 1);

        if (result == null) {
            throw new GeocodingException(location, "Geocode request returned null");
        }

        // Extract features list using type-safe helper
        List<Object> features = extractList(result, "features", location, "geocode result");

        if (features.isEmpty()) {
            throw new GeocodingException(location,
                String.format("No locations found for: %s. Please check spelling or try a different search term.", location));
        }

        // Validate first feature is a Map
        Object featureObj = features.getFirst();
        if (!(featureObj instanceof Map)) {
            throw new GeocodingException(location, "Unexpected feature format");
        }

        return (Map<String, Object>) featureObj;
    }

    /**
     * Attempts to parse a location string as coordinates in "lat,lng" format.
     *
     * @param location the location string to parse
     * @return Optional containing Location if parsing succeeds, empty otherwise
     */
    private Optional<Location> parseAsCoordinates(String location) {
        String[] coords = location.split(",");
        if (coords.length != 2) {
            return Optional.empty();
        }

        try {
            double lat = Double.parseDouble(coords[0].trim());
            double lng = Double.parseDouble(coords[1].trim());
            log.debug("Using provided coordinates: lat={}, lng={}", lat, lng);
            return Optional.of(new Location("coordinate", lat, lng));
        } catch (NumberFormatException e) {
            log.debug("Failed to parse as coordinates, will geocode: {}", location);
            return Optional.empty();
        }
    }

    /**
     * Extracts latitude and longitude from a GeoJSON coordinates list.
     * GeoJSON format is [lng, lat], which we convert to [lat, lng] in the return value.
     *
     * @param coordinates the GeoJSON coordinates list
     * @param location the location being processed (for error messages)
     * @return double array with [lat, lng]
     * @throws GeocodingException if coordinates are invalid
     */
    private double[] extractLatLng(List<Object> coordinates, String location) {
        if (coordinates.size() < 2) {
            throw new GeocodingException(location, "Invalid coordinates in feature");
        }

        try {
            Object lngObj = coordinates.get(0);
            Object latObj = coordinates.get(1);

            double lng = (lngObj instanceof Number) ? ((Number) lngObj).doubleValue() : 0.0;
            double lat = (latObj instanceof Number) ? ((Number) latObj).doubleValue() : 0.0;

            return new double[]{lat, lng};
        } catch (Exception e) {
            throw new GeocodingException(location, "Coordinates are not numbers", e);
        }
    }

    /**
     * Type-safe helper to extract a Map from a parent map with validation.
     *
     * @param map the parent map
     * @param key the key to extract
     * @param location the location being processed (for error messages)
     * @param fieldName the name of the field (for error messages)
     * @return the extracted Map
     * @throws GeocodingException if key is missing or value is not a Map
     */
    private Map<String, Object> extractMap(Map<String, Object> map, String key, String location, String fieldName) {
        if (!map.containsKey(key)) {
            throw new GeocodingException(location, String.format("Missing %s in %s", key, fieldName));
        }

        Object value = map.get(key);
        if (!(value instanceof Map)) {
            throw new GeocodingException(location, String.format("%s is not a map", fieldName));
        }

        return (Map<String, Object>) value;
    }

    /**
     * Type-safe helper to extract a List from a parent map with validation.
     *
     * @param map the parent map
     * @param key the key to extract
     * @param location the location being processed (for error messages)
     * @param fieldName the name of the field (for error messages)
     * @return the extracted List
     * @throws GeocodingException if key is missing or value is not a List
     */
    private List<Object> extractList(Map<String, Object> map, String key, String location, String fieldName) {
        if (!map.containsKey(key)) {
            throw new GeocodingException(location, String.format("Missing %s in %s", key, fieldName));
        }

        Object value = map.get(key);
        if (!(value instanceof List)) {
            throw new GeocodingException(location, String.format("%s is not a list", fieldName));
        }

        return (List<Object>) value;
    }
}
