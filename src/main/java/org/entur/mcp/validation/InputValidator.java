package org.entur.mcp.validation;

import org.entur.mcp.exception.ValidationException;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class InputValidator {

    private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    private static final int MAX_RESULTS_LIMIT = 50;
    private static final int MIN_RESULTS = 1;
    private static final int MAX_TIME_RANGE_MINUTES = 1440; // 24 hours
    private static final int MIN_TIME_RANGE_MINUTES = 1;


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
}
