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

    public static int validateAndNormalizeMaxResults(Integer maxResults, int defaultValue) {
        if (maxResults == null) {
            return defaultValue;
        }

        if (maxResults < MIN_RESULTS) {
            throw new ValidationException("maxResults",
                String.format("maxResults must be at least %d", MIN_RESULTS));
        }

        if (maxResults > MAX_RESULTS_LIMIT) {
            throw new ValidationException("maxResults",
                String.format("maxResults cannot exceed %d", MAX_RESULTS_LIMIT));
        }

        return maxResults;
    }

    public static void validateConflictingParameters(String departureTime, String arrivalTime) {
        if (departureTime != null && !departureTime.isBlank() &&
            arrivalTime != null && !arrivalTime.isBlank()) {
            throw new ValidationException("dateTime",
                "Cannot specify both departureTime and arrivalTime. Please provide only one.");
        }
    }

    public static int validateTimeRange(Integer timeRangeMinutes, int defaultValue) {
        if (timeRangeMinutes == null) {
            return defaultValue;
        }

        if (timeRangeMinutes < MIN_TIME_RANGE_MINUTES) {
            throw new ValidationException("timeRangeMinutes",
                String.format("timeRangeMinutes must be at least %d", MIN_TIME_RANGE_MINUTES));
        }

        if (timeRangeMinutes > MAX_TIME_RANGE_MINUTES) {
            throw new ValidationException("timeRangeMinutes",
                String.format("timeRangeMinutes cannot exceed %d (24 hours)", MAX_TIME_RANGE_MINUTES));
        }

        return timeRangeMinutes;
    }
}
