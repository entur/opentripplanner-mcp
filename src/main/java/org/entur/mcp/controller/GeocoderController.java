package org.entur.mcp.controller;

import org.entur.mcp.services.GeocoderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/geocode")
@CrossOrigin(origins = "*")
public class GeocoderController {

    private final GeocoderService geocoderService;

    public GeocoderController(@Autowired GeocoderService geocoderService) {
        this.geocoderService = geocoderService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> geocode(
            @RequestParam String text,
            @RequestParam(defaultValue = "5") int maxResults
    ) {
        Map<String, Object> result = geocoderService.handleGeocodeRequest(text, maxResults);
        return ResponseEntity.ok(result);
    }
}
