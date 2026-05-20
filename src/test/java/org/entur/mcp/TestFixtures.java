package org.entur.mcp;

import org.entur.mcp.model.Location;

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
}
