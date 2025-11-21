package org.entur.mcp.validation;

import org.entur.mcp.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InputValidator Tests")
class InputValidatorTest {

    // ==================== Location Validation Tests ====================

    @Test
    @DisplayName("Should throw exception when location is null")
    void validateLocation_withNullLocation_shouldThrowException() {
        assertThatThrownBy(() -> InputValidator.validateLocation(null, "location"))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("location cannot be null or empty")
            .matches(e -> ((ValidationException) e).getField().equals("location"));
    }

    @Test
    @DisplayName("Should throw exception when location is empty string")
    void validateLocation_withEmptyLocation_shouldThrowException() {
        assertThatThrownBy(() -> InputValidator.validateLocation("", "from"))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("from cannot be null or empty")
            .matches(e -> ((ValidationException) e).getField().equals("from"));
    }

    @Test
    @DisplayName("Should throw exception when location is blank (only whitespace)")
    void validateLocation_withBlankLocation_shouldThrowException() {
        assertThatThrownBy(() -> InputValidator.validateLocation("   ", "to"))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("to cannot be null or empty")
            .matches(e -> ((ValidationException) e).getField().equals("to"));
    }

    @Test
    @DisplayName("Should throw exception when location exceeds max length")
    void validateLocation_withTooLongLocation_shouldThrowException() {
        String longLocation = "a".repeat(501);

        assertThatThrownBy(() -> InputValidator.validateLocation(longLocation, "location"))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("exceeds maximum length of 500 characters");
    }

    @Test
    @DisplayName("Should pass validation with valid location")
    void validateLocation_withValidLocation_shouldPass() {
        assertThatCode(() -> InputValidator.validateLocation("Oslo S", "from"))
            .doesNotThrowAnyException();

        assertThatCode(() -> InputValidator.validateLocation("59.911,10.748", "location"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should pass validation with location at max length")
    void validateLocation_withMaxLengthLocation_shouldPass() {
        String maxLengthLocation = "a".repeat(500);

        assertThatCode(() -> InputValidator.validateLocation(maxLengthLocation, "location"))
            .doesNotThrowAnyException();
    }

    // ==================== DateTime Validation Tests ====================

    @Test
    @DisplayName("Should pass validation when dateTime is null (optional)")
    void validateDateTime_withNullDateTime_shouldPass() {
        assertThatCode(() -> InputValidator.validateDateTime(null, "departureTime"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should pass validation when dateTime is empty (optional)")
    void validateDateTime_withEmptyDateTime_shouldPass() {
        assertThatCode(() -> InputValidator.validateDateTime("", "departureTime"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw exception with invalid ISO 8601 format")
    void validateDateTime_withInvalidFormat_shouldThrowException() {
        assertThatThrownBy(() -> InputValidator.validateDateTime("2023-13-45", "departureTime"))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("departureTime")
            .hasMessageContaining("ISO 8601 format");
    }

    @Test
    @DisplayName("Should throw exception with completely invalid dateTime")
    void validateDateTime_withGarbageInput_shouldThrowException() {
        assertThatThrownBy(() -> InputValidator.validateDateTime("not-a-date", "arrivalTime"))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("arrivalTime")
            .hasMessageContaining("ISO 8601 format");
    }

    @Test
    @DisplayName("Should pass validation with valid ISO 8601 dateTime")
    void validateDateTime_withValidISO8601_shouldPass() {
        assertThatCode(() -> InputValidator.validateDateTime("2023-05-26T12:00:00", "departureTime"))
            .doesNotThrowAnyException();

        assertThatCode(() -> InputValidator.validateDateTime("2023-05-26T14:30:00+01:00", "arrivalTime"))
            .doesNotThrowAnyException();
    }

    // ==================== MaxResults Validation Tests ====================

    @Test
    @DisplayName("Should return default value when maxResults is null")
    void validateMaxResults_withNull_shouldReturnDefault() {
        int result = InputValidator.validateAndNormalizeMaxResults(null, 5);
        assertThat(result).isEqualTo(5);
    }

    @Test
    @DisplayName("Should throw exception when maxResults is negative")
    void validateMaxResults_withNegative_shouldThrowException() {
        assertThatThrownBy(() -> InputValidator.validateAndNormalizeMaxResults(-1, 5))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("maxResults must be at least 1")
            .matches(e -> ((ValidationException) e).getField().equals("maxResults"));
    }

    @Test
    @DisplayName("Should throw exception when maxResults is zero")
    void validateMaxResults_withZero_shouldThrowException() {
        assertThatThrownBy(() -> InputValidator.validateAndNormalizeMaxResults(0, 5))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("maxResults must be at least 1");
    }

    @Test
    @DisplayName("Should throw exception when maxResults exceeds limit")
    void validateMaxResults_withExceedingLimit_shouldThrowException() {
        assertThatThrownBy(() -> InputValidator.validateAndNormalizeMaxResults(51, 5))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("maxResults cannot exceed 50");
    }

    @Test
    @DisplayName("Should return value when maxResults is at minimum")
    void validateMaxResults_withMinimum_shouldReturnValue() {
        int result = InputValidator.validateAndNormalizeMaxResults(1, 5);
        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("Should return value when maxResults is at maximum")
    void validateMaxResults_withMaximum_shouldReturnValue() {
        int result = InputValidator.validateAndNormalizeMaxResults(50, 5);
        assertThat(result).isEqualTo(50);
    }

    @Test
    @DisplayName("Should return value when maxResults is valid")
    void validateMaxResults_withValidValue_shouldReturnValue() {
        int result = InputValidator.validateAndNormalizeMaxResults(10, 5);
        assertThat(result).isEqualTo(10);
    }

    // ==================== Conflicting Parameters Tests ====================

    @Test
    @DisplayName("Should throw exception when both departure and arrival times are set")
    void validateConflictingParameters_withBothSet_shouldThrowException() {
        assertThatThrownBy(() ->
            InputValidator.validateConflictingParameters("2023-05-26T10:00:00", "2023-05-26T14:00:00"))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Cannot specify both departureTime and arrivalTime");
    }

    @Test
    @DisplayName("Should pass validation with only departureTime")
    void validateConflictingParameters_withOnlyDeparture_shouldPass() {
        assertThatCode(() ->
            InputValidator.validateConflictingParameters("2023-05-26T10:00:00", null))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should pass validation with only arrivalTime")
    void validateConflictingParameters_withOnlyArrival_shouldPass() {
        assertThatCode(() ->
            InputValidator.validateConflictingParameters(null, "2023-05-26T14:00:00"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should pass validation with neither time set")
    void validateConflictingParameters_withNeitherSet_shouldPass() {
        assertThatCode(() ->
            InputValidator.validateConflictingParameters(null, null))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should pass validation when departureTime is blank and arrivalTime is set")
    void validateConflictingParameters_withBlankDepartureAndArrival_shouldPass() {
        assertThatCode(() ->
            InputValidator.validateConflictingParameters("", "2023-05-26T14:00:00"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should pass validation when both are blank")
    void validateConflictingParameters_withBothBlank_shouldPass() {
        assertThatCode(() ->
            InputValidator.validateConflictingParameters("", ""))
            .doesNotThrowAnyException();
    }

    // ==================== TimeRange Validation Tests ====================

    @Test
    @DisplayName("validateTimeRange should return default when null")
    void validateTimeRange_withNull_shouldReturnDefault() {
        assertThat(InputValidator.validateTimeRange(null, 60)).isEqualTo(60);
    }

    @Test
    @DisplayName("validateTimeRange should reject zero")
    void validateTimeRange_withZero_shouldThrow() {
        assertThatThrownBy(() -> InputValidator.validateTimeRange(0, 60))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("timeRangeMinutes must be at least 1");
    }

    @Test
    @DisplayName("validateTimeRange should reject negative values")
    void validateTimeRange_withNegative_shouldThrow() {
        assertThatThrownBy(() -> InputValidator.validateTimeRange(-10, 60))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("timeRangeMinutes must be at least 1");
    }

    @Test
    @DisplayName("validateTimeRange should reject values over 1440")
    void validateTimeRange_withTooLarge_shouldThrow() {
        assertThatThrownBy(() -> InputValidator.validateTimeRange(1441, 60))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("timeRangeMinutes cannot exceed 1440");
    }

    @Test
    @DisplayName("validateTimeRange should accept valid values")
    void validateTimeRange_withValidValue_shouldReturn() {
        assertThat(InputValidator.validateTimeRange(120, 60)).isEqualTo(120);
    }

    @Test
    @DisplayName("validateTimeRange should accept minimum valid value")
    void validateTimeRange_withMinimum_shouldReturn() {
        assertThat(InputValidator.validateTimeRange(1, 60)).isEqualTo(1);
    }

    @Test
    @DisplayName("validateTimeRange should accept maximum valid value")
    void validateTimeRange_withMaximum_shouldReturn() {
        assertThat(InputValidator.validateTimeRange(1440, 60)).isEqualTo(1440);
    }
}
