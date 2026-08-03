package org.entur.mcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.entur.mcp.model.Location;
import org.entur.mcp.tools.UiPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test fixtures and utility methods for creating test data
 */
public class TestFixtures {

    public static Location createOsloLocation() {
        return new Location("Oslo", 59.911076, 10.748128);
    }

    public static Location createAskerLocation() {
        return new Location("Asker", 59.832217, 10.433827);
    }

    public static String createGeocoderResponse(String name, double lat, double lng) {
        return String.format("""
            {
                "type": "FeatureCollection",
                "features": [{
                    "type": "Feature",
                    "geometry": {
                        "type": "Point",
                        "coordinates": [%.6f, %.6f]
                    },
                    "properties": {
                        "name": "%s",
                        "label": "%s"
                    }
                }]
            }
            """, lng, lat, name, name);
    }

    public static String createGeocoderResponseMultiple(int count) {
        StringBuilder json = new StringBuilder("""
            {
                "type": "FeatureCollection",
                "features": [
            """);

        for (int i = 0; i < count; i++) {
            if (i > 0) json.append(",");
            json.append(String.format("""
                {
                    "type": "Feature",
                    "geometry": {
                        "type": "Point",
                        "coordinates": [10.%d, 59.%d]
                    },
                    "properties": {
                        "name": "Location %d"
                    }
                }
                """, i, i, i));
        }

        json.append("]}");
        return json.toString();
    }

    public static String createOtpTripResponse() {
        return """
            {
                "data": {
                    "trip": {
                        "tripPatterns": [{
                            "duration": 1873,
                            "startTime": "2025-11-19T13:41:17+01:00",
                            "endTime": "2025-11-19T14:12:30+01:00",
                            "legs": [{
                                "mode": "foot",
                                "distance": 506.45,
                                "duration": 463,
                                "fromPlace": {"name": "Origin"},
                                "toPlace": {"name": "Oslo S"},
                                "emission": {"co2": 45.2}
                            }],
                            "emission": {"co2": 45.2}
                        }]
                    }
                }
            }
            """;
    }

    public static String createOtpErrorResponse(String errorMessage) {
        return String.format("""
            {
                "errors": [{
                    "message": "%s"
                }]
            }
            """, errorMessage);
    }

    public static String createEmptyGeocoderResponse() {
        return """
            {
                "type": "FeatureCollection",
                "features": []
            }
            """;
    }

    public static String createDepartureBoardResponse() {
        return """
            {
                "data": {
                    "stopPlace": {
                        "id": "NSR:StopPlace:337",
                        "name": "Oslo S",
                        "arrivals": [{
                            "aimedDepartureTime": "2025-01-21T14:30:00+01:00",
                            "expectedDepartureTime": "2025-01-21T14:30:00+01:00",
                            "actualDepartureTime": null,
                            "cancellation": false,
                            "realtime": true,
                            "realtimeState": "UPDATED",
                            "quay": {
                                "id": "NSR:Quay:566",
                                "publicCode": "19",
                                "name": "Oslo S"
                            },
                            "destinationDisplay": { "frontText": "Oslo S" },
                            "serviceJourney": {
                                "id": "VYG:ServiceJourney:123",
                                "line": {
                                    "id": "VYG:Line:R10",
                                    "publicCode": "R10",
                                    "name": "Drammen - Lillehammer",
                                    "transportMode": "rail"
                                }
                            },
                            "situations": [],
                            "empiricalDelay": {"p50": "PT2M30S", "p90": "PT5M"}
                        }],
                        "departures": [{
                            "aimedDepartureTime": "2025-01-21T14:30:00+01:00",
                            "expectedDepartureTime": "2025-01-21T14:32:00+01:00",
                            "actualDepartureTime": null,
                            "cancellation": false,
                            "realtime": true,
                            "realtimeState": "UPDATED",
                            "quay": {
                                "id": "NSR:Quay:566",
                                "publicCode": "19",
                                "name": "Oslo S"
                            },
                            "destinationDisplay": { "frontText": "Lillehammer" },
                            "serviceJourney": {
                                "id": "VYG:ServiceJourney:123",
                                "line": {
                                    "id": "VYG:Line:R10",
                                    "publicCode": "R10",
                                    "name": "Drammen - Lillehammer",
                                    "transportMode": "rail"
                                }
                            },
                            "situations": [],
                            "empiricalDelay": {"p50": "PT2M30S", "p90": "PT5M"}
                        }]
                    }
                }
            }
            """;
    }

    public static String createSituationsResponse() {
        return """
            {
                "data": {
                    "situations": [{
                        "id": "UHRTaXR1YXRpb25FbGVtZW50OlRFU1Q",
                        "situationNumber": "TST:SituationNumber:test-123",
                        "severity": "normal",
                        "reportType": "incident",
                        "summary": [
                            {"value": "Forsinkelser på linje 1", "language": "no"},
                            {"value": "Delays on line 1", "language": "en"}
                        ],
                        "description": [
                            {"value": "Det er tekniske problemer.", "language": "no"},
                            {"value": "There are technical issues.", "language": "en"}
                        ],
                        "validityPeriod": {
                            "startTime": "2026-04-27T06:00:00+02:00",
                            "endTime": "2026-04-27T22:00:00+02:00"
                        },
                        "affects": [{
                            "__typename": "AffectedLine",
                            "line": {
                                "publicCode": "1",
                                "name": "Frognerseteren - Helsfyr",
                                "transportMode": "metro"
                            }
                        }]
                    }]
                }
            }
            """;
    }

    public static String createNearestStopsResponse() {
        return """
            {
                "data": {
                    "nearest": {
                        "edges": [
                            {
                                "node": {
                                    "distance": 89.5,
                                    "place": {
                                        "__typename": "StopPlace",
                                        "id": "NSR:StopPlace:59601",
                                        "name": "Dronningens gate",
                                        "latitude": 59.910525,
                                        "longitude": 10.746901,
                                        "transportMode": ["tram", "bus"],
                                        "estimatedCalls": [
                                            {
                                                "expectedDepartureTime": "2026-04-27T10:05:00+02:00",
                                                "empiricalDelay": {"p50": "PT1M", "p90": "PT3M"},
                                                "destinationDisplay": {"frontText": "Majorstuen"},
                                                "serviceJourney": {
                                                    "line": {"publicCode": "12", "transportMode": "tram"}
                                                }
                                            }
                                        ]
                                    }
                                }
                            },
                            {
                                "node": {
                                    "distance": 149.2,
                                    "place": {
                                        "__typename": "StopPlace",
                                        "id": "NSR:StopPlace:58366",
                                        "name": "Jernbanetorget",
                                        "latitude": 59.911898,
                                        "longitude": 10.75038,
                                        "transportMode": ["tram", "metro", "bus"],
                                        "estimatedCalls": []
                                    }
                                }
                            }
                        ]
                    }
                }
            }
            """;
    }

    public static String createGeocoderResponseWithId(String name, String nsrId, double lat, double lng) {
        return String.format("""
            {
                "type": "FeatureCollection",
                "features": [{
                    "type": "Feature",
                    "geometry": {
                        "type": "Point",
                        "coordinates": [%.6f, %.6f]
                    },
                    "properties": {
                        "id": "%s",
                        "name": "%s",
                        "label": "%s"
                    }
                }]
            }
            """, lng, lat, nsrId, name, name);
    }

    public static String createMobilityVehiclesResponse() {
        return """
            {
                "data": {
                    "vehicles": [
                        {
                            "id": "YVO:Vehicle:abc-123",
                            "lat": 59.9123,
                            "lon": 10.7456,
                            "isReserved": false,
                            "currentRangeMeters": 12400.0,
                            "currentFuelPercent": 0.72,
                            "vehicleType": {
                                "formFactor": "SCOOTER_STANDING",
                                "propulsionType": "ELECTRIC"
                            },
                            "system": {
                                "id": "voi_oslo",
                                "name": {"translation": [{"language": "en", "value": "Voi Oslo"}]},
                                "operator": {
                                    "id": "YVO:Operator:voi",
                                    "name": {"translation": [{"language": "en", "value": "Voi"}]}
                                }
                            },
                            "rentalUris": {
                                "android": "voiapp://ride/abc-123",
                                "ios": "voiapp://ride/abc-123",
                                "web": "https://voi.example/ride/abc-123"
                            }
                        },
                        {
                            "id": "YVO:Vehicle:def-456",
                            "lat": 59.9150,
                            "lon": 10.7500,
                            "isReserved": false,
                            "currentRangeMeters": 8200.0,
                            "currentFuelPercent": 0.55,
                            "vehicleType": {
                                "formFactor": "SCOOTER_STANDING",
                                "propulsionType": "ELECTRIC"
                            },
                            "system": {
                                "id": "voi_oslo",
                                "name": {"translation": [{"language": "en", "value": "Voi Oslo"}]},
                                "operator": {
                                    "id": "YVO:Operator:voi",
                                    "name": {"translation": [{"language": "en", "value": "Voi"}]}
                                }
                            },
                            "rentalUris": null
                        }
                    ]
                }
            }
            """;
    }

    public static String createMobilityStationsResponse() {
        return """
            {
                "data": {
                    "stations": [
                        {
                            "id": "OBY:Station:1234",
                            "name": {"translation": [{"language": "nb", "value": "Oslo S"}]},
                            "lat": 59.9100,
                            "lon": 10.7500,
                            "numVehiclesAvailable": 6,
                            "vehicleTypesAvailable": [
                                {
                                    "count": 4,
                                    "vehicleType": {"formFactor": "BICYCLE", "propulsionType": "ELECTRIC_ASSIST"}
                                },
                                {
                                    "count": 2,
                                    "vehicleType": {"formFactor": "BICYCLE", "propulsionType": "HUMAN"}
                                }
                            ],
                            "system": {
                                "id": "oslobysykkel",
                                "name": {"translation": [{"language": "nb", "value": "Oslo Bysykkel"}]},
                                "operator": {
                                    "id": "OBY:Operator:oslobysykkel",
                                    "name": {"translation": [{"language": "nb", "value": "Oslo Bysykkel"}]}
                                }
                            },
                            "rentalUris": {
                                "android": "obyapp://station/1234",
                                "ios": "obyapp://station/1234",
                                "web": "https://oslobysykkel.no/station/1234"
                            }
                        }
                    ]
                }
            }
            """;
    }

    public static String createMobilityEmptyResponse() {
        return """
            {
                "data": {
                    "vehicles": [],
                    "stations": []
                }
            }
            """;
    }

    public static String createMobilityErrorResponse(String errorMessage) {
        return String.format("""
            {
                "errors": [{
                    "message": "%s"
                }]
            }
            """, errorMessage);
    }

    /** Two vehicles from the same system — exercises the de-duplication hoist. */
    public static String createMobilityVehiclesResponseTwoSameSystem() {
        return """
            {"data":{"vehicles":[
              {"id":"YVO:Vehicle:1","lat":59.911,"lon":10.750,"isReserved":false,
               "currentRangeMeters":8000,
               "system":{"id":"YOS:System:voi",
                         "name":{"translation":[{"language":"en","value":"Voi"}]},
                         "operator":{"id":"YOS:Operator:voi",
                                     "name":{"translation":[{"language":"en","value":"Voi"}]}}},
               "rentalUris":{"android":"http://a/1","ios":"http://i/1","web":"http://w/1"}},
              {"id":"YVO:Vehicle:2","lat":59.912,"lon":10.751,"isReserved":false,
               "currentRangeMeters":6000,
               "system":{"id":"YOS:System:voi",
                         "name":{"translation":[{"language":"en","value":"Voi"}]},
                         "operator":{"id":"YOS:Operator:voi",
                                     "name":{"translation":[{"language":"en","value":"Voi"}]}}},
               "rentalUris":{"android":"http://a/2","ios":"http://i/2","web":"http://w/2"}}
            ]}}""";
    }

    public static String createMobilityStationsResponseEmpty() {
        return "{\"data\":{\"stations\":[]}}";
    }

    /** findNearby-shaped map: one vehicle and one station, each carrying rentalUris. */
    public static Map<String, Object> createMobilityResponseMapWithRentalUris() {
        Map<String, Object> vehicle = new HashMap<>();
        vehicle.put("id", "YVO:Vehicle:1");
        vehicle.put("lat", 59.911);
        vehicle.put("lon", 10.750);
        vehicle.put("currentRangeMeters", 8000);
        vehicle.put("systemId", "YOS:System:voi");
        vehicle.put("rentalUris", new HashMap<>(Map.of("web", "http://w/1")));

        Map<String, Object> station = new HashMap<>();
        station.put("id", "YSB:Station:1");
        station.put("lat", 59.913);
        station.put("lon", 10.752);
        station.put("numVehiclesAvailable", 4);
        station.put("systemId", "YOS:System:bysykkel");
        station.put("rentalUris", new HashMap<>(Map.of("web", "http://w/s1")));

        Map<String, Object> root = new HashMap<>();
        root.put("vehicles", new ArrayList<>(List.of(vehicle)));
        root.put("stations", new ArrayList<>(List.of(station)));
        root.put("systems", new HashMap<>());
        return root;
    }

    /** Extracts the model-facing JSON text from a tool result. */
    public static String textOf(CallToolResult result) {
        return ((TextContent) result.content().get(0)).text();
    }

    /** Extracts the UI-only payload map, or null when the result carries no _meta. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> uiMetaOf(CallToolResult result) {
        if (result.meta() == null) {
            return null;
        }
        return (Map<String, Object>) result.meta().get(UiPayload.META_KEY);
    }

    /** OTP trip response shaped for split assertions: one pattern, one rail leg + one foot leg. */
    public static Map<String, Object> createTripResponseMapWithGeometry() {
        Map<String, Object> railLeg = new HashMap<>();
        railLeg.put("mode", "rail");
        railLeg.put("expectedStartTime", "2026-08-03T10:00:00+0200");
        railLeg.put("pointsOnLink", new HashMap<>(Map.of("points", "abcdef")));
        railLeg.put("intermediateEstimatedCalls",
            new ArrayList<>(List.of(Map.of("quay", Map.of("latitude", 59.9, "longitude", 10.7)))));
        railLeg.put("line", new HashMap<>(Map.of(
            "publicCode", "R11",
            "presentation", new HashMap<>(Map.of("colour", "FF0000", "textColour", "FFFFFF")))));
        railLeg.put("serviceJourney", new HashMap<>(Map.of("id", "NSR:ServiceJourney:1")));

        Map<String, Object> footLeg = new HashMap<>();
        footLeg.put("mode", "foot");
        footLeg.put("expectedStartTime", "2026-08-03T09:55:00+0200");

        Map<String, Object> pattern = new HashMap<>();
        pattern.put("duration", 1800);
        pattern.put("legs", new ArrayList<>(List.of(footLeg, railLeg)));

        Map<String, Object> root = new HashMap<>();
        root.put("trip", new HashMap<>(Map.of(
            "tripPatterns", new ArrayList<>(List.of(pattern)))));
        return root;
    }

    /** Departure board response with one arrival and one departure, each carrying line colours. */
    public static Map<String, Object> createDeparturesResponseMapWithPresentation() {
        Map<String, Object> root = new HashMap<>();
        root.put("stopPlace", new HashMap<>(Map.of(
            "id", "NSR:StopPlace:337",
            "name", "Oslo S",
            "arrivals", new ArrayList<>(List.of(estimatedCallFixture("R11"))),
            "departures", new ArrayList<>(List.of(estimatedCallFixture("L1"))))));
        return root;
    }

    private static Map<String, Object> estimatedCallFixture(String publicCode) {
        Map<String, Object> line = new HashMap<>();
        line.put("publicCode", publicCode);
        line.put("transportMode", "rail");
        line.put("presentation", new HashMap<>(Map.of("colour", "FF0000", "textColour", "FFFFFF")));

        Map<String, Object> call = new HashMap<>();
        call.put("expectedDepartureTime", "2026-08-03T10:00:00+0200");
        call.put("serviceJourney", new HashMap<>(Map.of(
            "id", "NSR:ServiceJourney:1", "line", line)));
        return call;
    }

    /** Nearest-stops response with one stop carrying coordinates and one call with line colours. */
    public static Map<String, Object> createNearbyStopsResponseMapWithPresentation() {
        Map<String, Object> place = new HashMap<>();
        place.put("id", "NSR:StopPlace:337");
        place.put("name", "Oslo S");
        place.put("latitude", 59.911491);
        place.put("longitude", 10.750500);
        place.put("estimatedCalls", new ArrayList<>(List.of(estimatedCallFixture("R11"))));

        Map<String, Object> node = new HashMap<>(Map.of("distance", 120.0, "place", place));
        Map<String, Object> root = new HashMap<>();
        root.put("nearest", new HashMap<>(Map.of(
            "edges", new ArrayList<>(List.of(new HashMap<>(Map.of("node", node)))))));
        return root;
    }
}
