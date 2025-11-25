package org.entur.mcp.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GeocodeRequest {

    @NotBlank(message = "text is required")
    @Size(max = 500, message = "text must not exceed 500 characters")
    @Schema(description = "Location text to search for",
            example = "Oslo rådhus")
    private String text;

    @Min(value = 1, message = "maxResults must be at least 1")
    @Max(value = 50, message = "maxResults cannot exceed 50")
    @Schema(description = "Maximum number of results to return",
            example = "10", defaultValue = "10")
    private Integer maxResults;

    // Getters and setters
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }
}
