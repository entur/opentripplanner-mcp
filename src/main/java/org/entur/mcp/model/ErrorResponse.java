package org.entur.mcp.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.HashMap;
import java.util.Map;

public class ErrorResponse {
    @Schema(description = "Error code", example = "VALIDATION_ERROR")
    private final String error;

    @Schema(description = "Human-readable error message", example = "from location is required")
    private final String message;

    @Schema(description = "Exception type", example = "ValidationException")
    private final String type;

    @Schema(description = "Additional error context")
    private final Map<String, String> details;

    private ErrorResponse(String error, String message, String type, Map<String, String> details) {
        this.error = error;
        this.message = message;
        this.type = type;
        this.details = details != null ? details : new HashMap<>();
    }

    public static ErrorResponse validationError(String field, String message) {
        Map<String, String> details = new HashMap<>();
        details.put("field", field);
        return new ErrorResponse("VALIDATION_ERROR", message, "ValidationException", details);
    }

    public static ErrorResponse geocodingError(String location, String message) {
        Map<String, String> details = new HashMap<>();
        details.put("location", location);
        return new ErrorResponse("GEOCODING_ERROR", message, "GeocodingException", details);
    }

    public static ErrorResponse tripPlanningError(String message) {
        return new ErrorResponse("TRIP_PLANNING_ERROR", message, "TripPlanningException", new HashMap<>());
    }

    public static ErrorResponse genericError(String message) {
        return new ErrorResponse("ERROR", message, "Exception", new HashMap<>());
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getType() {
        return type;
    }

    public Map<String, String> getDetails() {
        return details;
    }
}
