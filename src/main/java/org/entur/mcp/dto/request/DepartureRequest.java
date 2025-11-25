package org.entur.mcp.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public class DepartureRequest {

    @NotBlank(message = "stop is required")
    @Size(max = 500, message = "stop must not exceed 500 characters")
    @Schema(description = "Stop place name (e.g., 'Oslo S', 'Bergen stasjon') or NSR ID (e.g., NSR:StopPlace:337)",
            example = "Oslo S")
    private String stop;

    @Min(value = 1, message = "numberOfDepartures must be at least 1")
    @Max(value = 50, message = "numberOfDepartures cannot exceed 50")
    @Schema(description = "Number of departures to return",
            example = "10", defaultValue = "10")
    private Integer numberOfDepartures;

    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*$",
             message = "startTime must be in ISO 8601 format")
    @Schema(description = "Start time in ISO 8601 format (default: now)",
            example = "2023-05-26T12:00:00")
    private String startTime;

    @Min(value = 1, message = "timeRangeMinutes must be at least 1")
    @Max(value = 1440, message = "timeRangeMinutes cannot exceed 1440 (24 hours)")
    @Schema(description = "Time range in minutes to search (max 24 hours)",
            example = "60", defaultValue = "60")
    private Integer timeRangeMinutes;

    @Schema(description = "Filter by transport modes (e.g., 'rail', 'bus', 'tram', 'metro', 'water', 'air')",
            example = "[\"rail\", \"bus\"]")
    private List<String> transportModes;

    // Getters and setters
    public String getStop() {
        return stop;
    }

    public void setStop(String stop) {
        this.stop = stop;
    }

    public Integer getNumberOfDepartures() {
        return numberOfDepartures;
    }

    public void setNumberOfDepartures(Integer numberOfDepartures) {
        this.numberOfDepartures = numberOfDepartures;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public Integer getTimeRangeMinutes() {
        return timeRangeMinutes;
    }

    public void setTimeRangeMinutes(Integer timeRangeMinutes) {
        this.timeRangeMinutes = timeRangeMinutes;
    }

    public List<String> getTransportModes() {
        return transportModes;
    }

    public void setTransportModes(List<String> transportModes) {
        this.transportModes = transportModes;
    }
}
