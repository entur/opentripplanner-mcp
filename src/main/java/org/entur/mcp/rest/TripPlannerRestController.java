package org.entur.mcp.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.entur.mcp.dto.request.DepartureRequest;
import org.entur.mcp.dto.request.GeocodeRequest;
import org.entur.mcp.dto.request.TripRequest;
import org.entur.mcp.model.ErrorResponse;
import org.entur.mcp.services.GeocoderService;
import org.entur.mcp.services.OtpSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Trip Planning",
     description = "Journey planning, geocoding, and departure board APIs for Norwegian public transport")
public class TripPlannerRestController {

    private static final Logger log = LoggerFactory.getLogger(TripPlannerRestController.class);

    private final OtpSearchService otpSearchService;
    private final GeocoderService geocoderService;

    public TripPlannerRestController(
        OtpSearchService otpSearchService,
        GeocoderService geocoderService
    ) {
        this.otpSearchService = otpSearchService;
        this.geocoderService = geocoderService;
    }

    @Operation(
        summary = "Plan a multi-modal journey",
        description = """
                Plan multi-leg public transport routes between two locations across Norway and the Nordic region.
                Use when the user needs journey options with departure/arrival times, transfers, and trip duration.
                For real-time departures from a specific stop, use the departures tool instead."""
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Trip plan successfully generated"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters (validation error or geocoding failure)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Trip planning service error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @GetMapping("/trips")
    public ResponseEntity<Map<String, Object>> planTrip(@Valid @ModelAttribute TripRequest request) {
        log.debug("REST API: Planning trip from '{}' to '{}'", request.getFrom(), request.getTo());

        Map<String, Object> result = otpSearchService.handleTripRequest(
            request.getFrom(),
            request.getTo(),
            request.getDepartureTime(),
            request.getArrivalTime(),
            request.getMaxResults()
        );

        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Geocode a location",
        description = "Convert place names or addresses into geographic coordinates with metadata. " +
                     "Returns GeoJSON features with coordinates, names, and additional location information."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Location successfully geocoded"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Geocoding service error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @GetMapping("/geocode")
    public ResponseEntity<Map<String, Object>> geocode(@Valid @ModelAttribute GeocodeRequest request) {
        log.debug("REST API: Geocoding location '{}'", request.getText());

        Map<String, Object> result = geocoderService.handleGeocodeRequest(
            request.getText(),
            request.getMaxResults() != null ? request.getMaxResults() : 10
        );

        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Get real-time departures",
        description ="""
                        Get real-time departures from a single stop or station in Norway. Use when the user
                        wants to see what's leaving soon from a specific location. Shows upcoming vehicles with
                        line numbers, destinations, platforms, and live delay information. For planning routes
                        between two locations, use the trip tool instead.
                        """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Departures successfully retrieved"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters or stop not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Departure board service error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @GetMapping("/departures")
    public ResponseEntity<Map<String, Object>> getDepartures(@Valid @ModelAttribute DepartureRequest request) {
        log.debug("REST API: Getting departures for stop '{}'", request.getStop());

        // Resolve stop name to ID
        String stopId = geocoderService.resolveStopId(request.getStop());

        Map<String, Object> result = otpSearchService.handleDepartureBoardRequest(
            stopId,
            request.getNumberOfDepartures(),
            request.getStartTime(),
            request.getTimeRangeMinutes(),
            request.getTransportModes()
        );

        return ResponseEntity.ok(result);
    }
}
