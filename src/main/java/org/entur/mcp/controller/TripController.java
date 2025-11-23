package org.entur.mcp.controller;

import org.entur.mcp.exception.GeocodingException;
import org.entur.mcp.exception.TripPlanningException;
import org.entur.mcp.exception.ValidationException;
import org.entur.mcp.services.OtpSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/trip")
@CrossOrigin(origins = "*")
public class TripController {
    private static final Logger log = LoggerFactory.getLogger(TripController.class);

    private final OtpSearchService otpSearchService;

    public TripController(@Autowired OtpSearchService otpSearchService) {
        this.otpSearchService = otpSearchService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> planTrip(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String departureTime,
            @RequestParam(required = false) String arrivalTime,
            @RequestParam(required = false) Integer maxResults,
            @RequestParam(required = false) String fromName,
            @RequestParam(required = false) String toName
    ) {
        try {
            log.debug("Trip request: from='{}', to='{}'", from, to);

            Map<String, Object> tripResponse = otpSearchService.handleTripRequest(
                from, to, departureTime, arrivalTime, maxResults);

            // Build response with location info for UI
            Map<String, Object> response = new HashMap<>();
            response.putAll(tripResponse);
            response.put("from", Map.of("place", fromName != null ? fromName : from));
            response.put("to", Map.of("place", toName != null ? toName : to));

            return ResponseEntity.ok(response);

        } catch (ValidationException e) {
            log.warn("Validation error: {} - {}", e.getField(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", "validation_error",
                "field", e.getField(),
                "message", e.getMessage()
            ));

        } catch (GeocodingException e) {
            log.warn("Geocoding error: {} - {}", e.getLocation(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", "geocoding_error",
                "location", e.getLocation(),
                "message", e.getMessage()
            ));

        } catch (TripPlanningException e) {
            log.error("Trip planning error: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "trip_planning_error",
                "message", e.getMessage()
            ));

        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "internal_error",
                "message", "An unexpected error occurred"
            ));
        }
    }
}
