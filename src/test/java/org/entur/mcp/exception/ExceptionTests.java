package org.entur.mcp.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Exception Tests")
class ExceptionTests {

    @Test
    @DisplayName("ValidationException should store field and message")
    void validationException_shouldStoreFieldAndMessage() {
        ValidationException exception = new ValidationException("from", "from cannot be empty");

        assertThat(exception.getField()).isEqualTo("from");
        assertThat(exception.getMessage()).isEqualTo("from cannot be empty");
    }

    @Test
    @DisplayName("ValidationException should be a RuntimeException")
    void validationException_shouldBeRuntimeException() {
        ValidationException exception = new ValidationException("test", "test message");

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("GeocodingException should store location and message")
    void geocodingException_shouldStoreLocationAndMessage() {
        GeocodingException exception = new GeocodingException("Oslo S", "Location not found");

        assertThat(exception.getLocation()).isEqualTo("Oslo S");
        assertThat(exception.getMessage()).isEqualTo("Location not found");
    }

    @Test
    @DisplayName("GeocodingException should store location, message, and cause")
    void geocodingException_withCause_shouldStoreCause() {
        Throwable cause = new RuntimeException("Network error");
        GeocodingException exception = new GeocodingException("Oslo S", "Failed to geocode", cause);

        assertThat(exception.getLocation()).isEqualTo("Oslo S");
        assertThat(exception.getMessage()).isEqualTo("Failed to geocode");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("GeocodingException should be a RuntimeException")
    void geocodingException_shouldBeRuntimeException() {
        GeocodingException exception = new GeocodingException("test", "test message");

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("TripPlanningException should store message")
    void tripPlanningException_shouldStoreMessage() {
        TripPlanningException exception = new TripPlanningException("Failed to plan trip");

        assertThat(exception.getMessage()).isEqualTo("Failed to plan trip");
    }

    @Test
    @DisplayName("TripPlanningException should store message and cause")
    void tripPlanningException_withCause_shouldStoreCause() {
        Throwable cause = new RuntimeException("API error");
        TripPlanningException exception = new TripPlanningException("Failed to plan trip", cause);

        assertThat(exception.getMessage()).isEqualTo("Failed to plan trip");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("TripPlanningException should be a RuntimeException")
    void tripPlanningException_shouldBeRuntimeException() {
        TripPlanningException exception = new TripPlanningException("test message");

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
