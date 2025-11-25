package org.entur.mcp.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class TripRequest {

    @NotBlank(message = "from location is required")
    @Size(max = 500, message = "from location must not exceed 500 characters")
    @Schema(description = "Starting location (address, place name, or lat,lng coordinates)",
            example = "Oslo S")
    private String from;

    @NotBlank(message = "to location is required")
    @Size(max = 500, message = "to location must not exceed 500 characters")
    @Schema(description = "Destination location (address, place name, or lat,lng coordinates)",
            example = "Bergen stasjon")
    private String to;

    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*$",
             message = "departureTime must be in ISO 8601 format")
    @Schema(description = "Departure time in ISO 8601 format",
            example = "2023-05-26T12:00:00")
    private String departureTime;

    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*$",
             message = "arrivalTime must be in ISO 8601 format")
    @Schema(description = "Arrival time in ISO 8601 format",
            example = "2023-05-26T14:00:00")
    private String arrivalTime;

    @Min(value = 1, message = "maxResults must be at least 1")
    @Max(value = 50, message = "maxResults cannot exceed 50")
    @Schema(description = "Maximum number of trip options to return",
            example = "3", defaultValue = "3")
    private Integer maxResults;

    // Getters and setters
    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(String arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }
}
