package org.entur.mcp.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import org.entur.mcp.exception.MobilityException;
import org.entur.mcp.metrics.MetricsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
@Timed(value = "mcp.mobility.service", description = "Requests towards the Entur Mobility service")
public class MobilityService {

    private static final Logger log = LoggerFactory.getLogger(MobilityService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String vehiclesQuery = """
        query Vehicles($lat: Float!, $lon: Float!, $range: Int!, $formFactors: [FormFactor!], $propulsionTypes: [PropulsionType!], $operators: [String!], $count: Int!) {
          vehicles(lat: $lat, lon: $lon, range: $range, formFactors: $formFactors, propulsionTypes: $propulsionTypes, operators: $operators, count: $count) {
            id
            lat
            lon
            isReserved
            currentRangeMeters
            currentFuelPercent
            vehicleType { formFactor propulsionType }
            system {
              id
              name { translation { language value } }
              operator { id name { translation { language value } } }
            }
            rentalUris { android ios web }
          }
        }
        """;

    private static final String stationsQuery = """
        query Stations($lat: Float!, $lon: Float!, $range: Int!, $formFactors: [FormFactor!], $propulsionTypes: [PropulsionType!], $operators: [String!], $count: Int!) {
          stations(lat: $lat, lon: $lon, range: $range, availableFormFactors: $formFactors, availablePropulsionTypes: $propulsionTypes, operators: $operators, count: $count) {
            id
            name { translation { language value } }
            lat
            lon
            numVehiclesAvailable
            vehicleTypesAvailable {
              count
              vehicleType { formFactor propulsionType }
            }
            system {
              id
              name { translation { language value } }
              operator { id name { translation { language value } } }
            }
            rentalUris { android ios web }
          }
        }
        """;

    private final String mobilityURL;
    private final String etClientName;
    private final HttpClient client;

    public MobilityService(
            @Value("${org.entur.mobility.url}") String mobilityURL,
            @Value("${org.entur.mcp.client_name:entur-mcp}") String etClientName) {
        this.mobilityURL = mobilityURL;
        this.etClientName = etClientName;
        this.client = HttpClient.newHttpClient();
        log.info("Initializing MobilityService with mobilityURL='{}', etClientName='{}'", mobilityURL, etClientName);
    }

    public Map<String, Object> findNearby(double lat, double lon, int radius,
                                          List<String> formFactors, List<String> propulsionTypes,
                                          List<String> operators, int maxResults) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("lat", lat);
        variables.put("lon", lon);
        variables.put("range", radius);
        variables.put("count", maxResults);
        variables.put("formFactors", formFactors);
        variables.put("propulsionTypes", propulsionTypes);
        variables.put("operators", operators);

        CompletableFuture<Map<String, Object>> vehiclesFuture =
            sendQueryAsync(vehiclesQuery, variables);
        CompletableFuture<Map<String, Object>> stationsFuture =
            sendQueryAsync(stationsQuery, variables);

        Map<String, Object> vehiclesData;
        Map<String, Object> stationsData;
        try {
            vehiclesData = vehiclesFuture.get();
            stationsData = stationsFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MobilityException("Mobility query was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof MobilityException me) throw me;
            throw new MobilityException("Mobility query failed", cause);
        }

        List<Map<String, Object>> vehicles = extractList(vehiclesData, "vehicles");
        List<Map<String, Object>> stations = extractList(stationsData, "stations");

        attachDistanceAndSort(vehicles, lat, lon);
        attachDistanceAndSort(stations, lat, lon);

        Map<String, Object> result = new HashMap<>();
        result.put("vehicles", vehicles);
        result.put("stations", stations);
        return result;
    }

    private CompletableFuture<Map<String, Object>> sendQueryAsync(String query, Map<String, Object> variables) {
        Map<String, Object> reqBody = new HashMap<>();
        reqBody.put("query", query);
        reqBody.put("variables", variables);

        String reqJSON;
        try {
            reqJSON = objectMapper.writeValueAsString(reqBody);
        } catch (Exception e) {
            CompletableFuture<Map<String, Object>> failed = new CompletableFuture<>();
            failed.completeExceptionally(new MobilityException("Failed to serialize mobility request", e));
            return failed;
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(mobilityURL))
            .header("Content-Type", "application/json")
            .header(MetricsUtils.ET_CLIENT_NAME_HEADER, etClientName)
            .POST(HttpRequest.BodyPublishers.ofString(reqJSON))
            .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(this::parseResponse);
    }

    private Map<String, Object> parseResponse(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            log.error("Mobility API returned status {}: {}", response.statusCode(), response.body());
            throw new MobilityException(
                String.format("Mobility API returned status %d", response.statusCode()));
        }
        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(response.body(), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to parse mobility response: {}", e.getMessage());
            throw new MobilityException("Invalid response format from mobility API", e);
        }
        if (parsed.containsKey("errors")) {
            List<?> errors = (List<?>) parsed.get("errors");
            if (errors != null && !errors.isEmpty()) {
                log.error("Mobility GraphQL returned errors: {}", errors);
                throw new MobilityException(String.format("Mobility query failed: %s", errors));
            }
        }
        Map<String, Object> data = (Map<String, Object>) parsed.get("data");
        if (data == null) {
            throw new MobilityException("No data returned from mobility API");
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractList(Map<String, Object> data, String key) {
        Object raw = data.get(key);
        if (!(raw instanceof List<?> list)) return Collections.emptyList();
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    private static void attachDistanceAndSort(List<Map<String, Object>> items, double originLat, double originLon) {
        for (Map<String, Object> item : items) {
            double itemLat = ((Number) item.get("lat")).doubleValue();
            double itemLon = ((Number) item.get("lon")).doubleValue();
            int distance = (int) Math.round(haversineMeters(originLat, originLon, itemLat, itemLon));
            item.put("distanceMeters", distance);
        }
        items.sort((a, b) -> {
            int da = (int) a.get("distanceMeters");
            int db = (int) b.get("distanceMeters");
            if (da != db) return Integer.compare(da, db);
            String ida = String.valueOf(a.get("id"));
            String idb = String.valueOf(b.get("id"));
            return ida.compareTo(idb);
        });
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusM = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusM * c;
    }
}
