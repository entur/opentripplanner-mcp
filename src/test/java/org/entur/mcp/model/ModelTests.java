package org.entur.mcp.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Model Tests")
class ModelTests {

    // ==================== Location Tests ====================

    @Test
    @DisplayName("Location should store all fields correctly")
    void location_shouldStoreAllFields() {
        Location location = new Location("Oslo", 59.911, 10.748);

        assertThat(location.getPlace()).isEqualTo("Oslo");
        assertThat(location.getLatitude()).isEqualTo(59.911);
        assertThat(location.getLongitude()).isEqualTo(10.748);
    }

    @Test
    @DisplayName("Location setters should update fields")
    void location_setters_shouldUpdateFields() {
        Location location = new Location("Oslo", 59.911, 10.748);

        location.setPlace("Asker");
        location.setLatitude(59.832);
        location.setLongitude(10.433);

        assertThat(location.getPlace()).isEqualTo("Asker");
        assertThat(location.getLatitude()).isEqualTo(59.832);
        assertThat(location.getLongitude()).isEqualTo(10.433);
    }

    // ==================== ErrorResponse Tests ====================

    @Test
    @DisplayName("ErrorResponse.validationError should create correct structure")
    void errorResponse_validationError_shouldCreateCorrectStructure() {
        ErrorResponse error = ErrorResponse.validationError("from", "from cannot be empty");

        assertThat(error.getError()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.getMessage()).isEqualTo("from cannot be empty");
        assertThat(error.getType()).isEqualTo("ValidationException");
        assertThat(error.getDetails()).containsEntry("field", "from");
    }

    @Test
    @DisplayName("ErrorResponse.geocodingError should create correct structure")
    void errorResponse_geocodingError_shouldCreateCorrectStructure() {
        ErrorResponse error = ErrorResponse.geocodingError("Oslo S", "Location not found");

        assertThat(error.getError()).isEqualTo("GEOCODING_ERROR");
        assertThat(error.getMessage()).isEqualTo("Location not found");
        assertThat(error.getType()).isEqualTo("GeocodingException");
        assertThat(error.getDetails()).containsEntry("location", "Oslo S");
    }

    @Test
    @DisplayName("ErrorResponse.tripPlanningError should create correct structure")
    void errorResponse_tripPlanningError_shouldCreateCorrectStructure() {
        ErrorResponse error = ErrorResponse.tripPlanningError("Failed to plan trip");

        assertThat(error.getError()).isEqualTo("TRIP_PLANNING_ERROR");
        assertThat(error.getMessage()).isEqualTo("Failed to plan trip");
        assertThat(error.getType()).isEqualTo("TripPlanningException");
        assertThat(error.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("ErrorResponse.genericError should create correct structure")
    void errorResponse_genericError_shouldCreateCorrectStructure() {
        ErrorResponse error = ErrorResponse.genericError("An unexpected error occurred");

        assertThat(error.getError()).isEqualTo("ERROR");
        assertThat(error.getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(error.getType()).isEqualTo("Exception");
        assertThat(error.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("ErrorResponse details should be non-null")
    void errorResponse_details_shouldBeNonNull() {
        ErrorResponse error = ErrorResponse.genericError("test");

        assertThat(error.getDetails()).isNotNull();
        assertThat(error.getDetails()).isInstanceOf(Map.class);
    }
}
