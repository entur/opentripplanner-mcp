package org.entur.mcp.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.entur.mcp.model.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@DisplayName("GeocoderService Integration Tests")
class GeocoderServiceIntegrationTest {

    @Autowired
    private GeocoderService geocoderService;

    @Test
    @DisplayName("Should geocode real location 'Oslo S'")
    void geocodeRealLocation_shouldReturnResults() throws JsonProcessingException {
        // Act
        Map<String, Object> result = geocoderService.handleGeocodeRequest("Oslo S", 5);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsKey("features");

        @SuppressWarnings("unchecked")
        List<Object> features = (List<Object>) result.get("features");
        assertThat(features).isNotEmpty();
        assertThat(features.size()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("Should geocode Asker Stasjon")
    void geocodeAskerStasjon_shouldReturnResults() throws JsonProcessingException {
        // Act
        Map<String, Object> result = geocoderService.handleGeocodeRequest("Asker Stasjon", 3);

        // Assert
        assertThat(result).isNotNull();

        @SuppressWarnings("unchecked")
        List<Object> features = (List<Object>) result.get("features");
        assertThat(features).isNotEmpty();
    }

    @Test
    @DisplayName("Should handle coordinates string without calling API")
    void geocodeIfNeeded_withCoordinates_shouldNotCallAPI() throws JsonProcessingException {
        // Act
        Location location = geocoderService.geocodeIfNeeded("59.911,10.748");

        // Assert
        assertThat(location).isNotNull();
        assertThat(location.getLatitude()).isEqualTo(59.911, within(0.001));
        assertThat(location.getLongitude()).isEqualTo(10.748, within(0.001));
        assertThat(location.getPlace()).isEqualTo("coordinate");
    }

    @Test
    @DisplayName("Should geocode place name to location")
    void geocodeIfNeeded_withPlaceName_shouldReturnLocation() throws JsonProcessingException {
        // Act
        Location location = geocoderService.geocodeIfNeeded("Oslo S");

        // Assert
        assertThat(location).isNotNull();
        assertThat(location.getPlace()).isNotNull();
        assertThat(location.getLatitude()).isBetween(55.0, 71.5);
        assertThat(location.getLongitude()).isBetween(4.0, 31.5);
    }

    @Test
    @DisplayName("Should limit results to maxResults parameter")
    void handleGeocodeRequest_shouldLimitResults() throws JsonProcessingException {
        // Act
        Map<String, Object> result1 = geocoderService.handleGeocodeRequest("Oslo", 1);
        Map<String, Object> result10 = geocoderService.handleGeocodeRequest("Oslo", 10);

        // Assert
        @SuppressWarnings("unchecked")
        List<Object> features1 = (List<Object>) result1.get("features");
        @SuppressWarnings("unchecked")
        List<Object> features10 = (List<Object>) result10.get("features");

        assertThat(features1).hasSize(1);
        assertThat(features10.size()).isLessThanOrEqualTo(10);
    }
}