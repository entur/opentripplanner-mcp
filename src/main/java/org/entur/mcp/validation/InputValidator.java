package org.entur.mcp.validation;

import org.entur.mcp.exception.ValidationException;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class InputValidator {

    private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    private static final int MAX_RESULTS_LIMIT = 50;
    private static final Set<String> VALID_SEVERITIES = Set.of(
        "noImpact", "verySlight", "slight", "normal", "severe", "verySevere"
    );
    private static final Set<String> VALID_FORM_FACTORS = Set.of(
        "BICYCLE", "CARGO_BICYCLE", "CAR", "MOPED",
        "SCOOTER_STANDING", "SCOOTER_SEATED", "OTHER"
    );
    private static final Set<String> VALID_PROPULSION_TYPES = Set.of(
        "HUMAN", "ELECTRIC_ASSIST", "ELECTRIC", "COMBUSTION",
        "COMBUSTION_DIESEL", "HYBRID", "PLUG_IN_HYBRID", "HYDROGEN_FUEL_CELL"
    );
    private static final Set<String> VALID_TRANSPORT_MODES = Set.of(
        "air", "bus", "cableway", "water", "funicular", "lift", "rail",
        "metro", "taxi", "tram", "trolleybus", "monorail", "coach", "unknown"
    );
    private static final Pattern NSR_STOP_ID = Pattern.compile("^NSR:(StopPlace|Quay):[0-9]+$");
    private static final int MIN_RESULTS = 1;
    private static final int MAX_TIME_RANGE_MINUTES = 1440; // 24 hours
    private static final int MIN_TIME_RANGE_MINUTES = 1;
    private static final int MAX_RADIUS_METERS = 2000;
    private static final int MIN_RADIUS_METERS = 1;
    // No enum has more than 14 values, so 20 leaves room for duplicates without allowing bulk input
    private static final int MAX_LIST_SIZE = 20;
    private static final int MAX_ECHOED_INVALID_VALUES = 5;
    private static final int MAX_ECHOED_VALUE_LENGTH = 40;


    public static void validateLocation(String location, String fieldName) {
        if (location == null || location.isBlank()) {
            throw new ValidationException(fieldName,
                String.format("%s cannot be null or empty", fieldName));
        }

        if (location.trim().length() > 500) {
            throw new ValidationException(fieldName,
                String.format("%s exceeds maximum length of 500 characters", fieldName));
        }
    }

    public static boolean isNsrStopId(String value) {
        return value != null && NSR_STOP_ID.matcher(value).matches();
    }

    public static void validateDateTime(String dateTime, String fieldName) {
        if (dateTime == null || dateTime.isBlank()) {
            return; // Optional parameter
        }

        try {
            ISO_DATE_TIME_FORMATTER.parse(dateTime);
        } catch (DateTimeParseException e) {
            throw new ValidationException(fieldName,
                String.format("%s must be in ISO 8601 format (e.g., 2023-05-26T12:00:00). Error: %s",
                    fieldName, e.getMessage()));
        }
    }

    /**
     * Validates that an integer value is within a specified range.
     *
     * @param value the value to validate (can be null)
     * @param fieldName the name of the field being validated
     * @param min the minimum allowed value (inclusive)
     * @param max the maximum allowed value (inclusive)
     * @param defaultValue the default value to return if value is null
     * @return the validated value or the default value if null
     * @throws ValidationException if the value is outside the allowed range
     */
    private static int validateRange(Integer value, String fieldName, int min, int max, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        if (value < min) {
            throw new ValidationException(fieldName,
                String.format("%s must be at least %d", fieldName, min));
        }

        if (value > max) {
            throw new ValidationException(fieldName,
                String.format("%s cannot exceed %d", fieldName, max));
        }

        return value;
    }

    public static int validateAndNormalizeMaxResults(Integer maxResults, int defaultValue) {
        return validateRange(maxResults, "maxResults", MIN_RESULTS, MAX_RESULTS_LIMIT, defaultValue);
    }

    public static void validateConflictingParameters(String departureTime, String arrivalTime) {
        if (departureTime != null && !departureTime.isBlank() &&
            arrivalTime != null && !arrivalTime.isBlank()) {
            throw new ValidationException("dateTime",
                "Cannot specify both departureTime and arrivalTime. Please provide only one.");
        }
    }

    public static int validateTimeRange(Integer timeRangeMinutes, int defaultValue) {
        return validateRange(timeRangeMinutes, "timeRangeMinutes",
            MIN_TIME_RANGE_MINUTES, MAX_TIME_RANGE_MINUTES, defaultValue);
    }

    public static int validateRadius(Integer radiusMeters, int defaultValue) {
        return validateRange(radiusMeters, "radiusMeters", MIN_RADIUS_METERS, MAX_RADIUS_METERS, defaultValue);
    }

    public static List<String> validateSeverities(List<String> severities) {
        return validateEnumList(severities, VALID_SEVERITIES, "severities", v -> v);
    }

    public static List<String> validateFormFactors(List<String> formFactors) {
        return validateEnumList(formFactors, VALID_FORM_FACTORS, "formFactors", v -> v.toUpperCase());
    }

    public static List<String> validatePropulsionTypes(List<String> propulsionTypes) {
        return validateEnumList(propulsionTypes, VALID_PROPULSION_TYPES, "propulsionTypes", v -> v.toUpperCase());
    }

    public static List<String> validateTransportModes(List<String> transportModes) {
        return validateEnumList(transportModes, VALID_TRANSPORT_MODES, "transportModes", v -> v.toLowerCase());
    }

    /**
     * Checks every value against {@code allowed} and returns the normalized values without
     * duplicates. The list is capped first, so an oversized one is rejected before any work is
     * done on it, and the error names only a few short samples of the invalid values instead of
     * echoing the caller's whole input back.
     */
    private static List<String> validateEnumList(List<String> values, Set<String> allowed, String fieldName,
                                                 UnaryOperator<String> normalize) {
        if (values == null || values.isEmpty()) return values;

        if (values.size() > MAX_LIST_SIZE) {
            throw new ValidationException(fieldName,
                String.format("%s accepts at most %d values. Valid values: %s", fieldName, MAX_LIST_SIZE, allowed));
        }

        List<String> normalized = values.stream()
            .map(v -> v == null ? "" : normalize.apply(v.trim()))
            .distinct()
            .collect(Collectors.toList());

        List<String> invalid = normalized.stream()
            .filter(v -> !allowed.contains(v))
            .limit(MAX_ECHOED_INVALID_VALUES)
            .map(v -> v.length() > MAX_ECHOED_VALUE_LENGTH ? v.substring(0, MAX_ECHOED_VALUE_LENGTH) + "…" : v)
            .collect(Collectors.toList());

        if (!invalid.isEmpty()) {
            throw new ValidationException(fieldName,
                String.format("Invalid %s values: %s. Valid values: %s", fieldName, invalid, allowed));
        }
        return normalized;
    }

    /**
     * Rejects coordinates that cannot be a place on Earth. {@link Double#parseDouble} accepts
     * "NaN", "Infinity" and values like 1e400, none of which OTP can use.
     */
    public static void validateCoordinates(double latitude, double longitude, String fieldName) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new ValidationException(fieldName,
                String.format("%s: latitude must be a number between -90 and 90", fieldName));
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new ValidationException(fieldName,
                String.format("%s: longitude must be a number between -180 and 180", fieldName));
        }
    }
}
