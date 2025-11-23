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
                            "expectedStartTime": "2025-11-19T13:41:17+01:00",
                            "expectedEndTime": "2025-11-19T14:12:30+01:00",
                            "legs": [{
                                "mode": "foot",
                                "distance": 506.45,
                                "duration": 463,
                                "fromPlace": {
                                    "name": "Origin",
                                    "latitude": 59.911076,
                                    "longitude": 10.748128
                                },
                                "toPlace": {
                                    "name": "Oslo S",
                                    "latitude": 59.910911,
                                    "longitude": 10.750491
                                },
                                "pointsOnLink": {
                                    "points": "o~slJauzb@Bb@FXHPJJLDHBD@D?H?HAF?@AFCBADCBQPs@n@wAfAe@^u@h@]Xa@\\\\g@`@WRYRg@^g@^_@Xg@`@g@`@i@b@a@ZYVw@j@"
                                }
                            }]
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
                        "estimatedCalls": [{
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
                            "destinationDisplay": {
                                "frontText": "Lillehammer"
                            },
                            "serviceJourney": {
                                "id": "VYG:ServiceJourney:123",
                                "line": {
                                    "id": "VYG:Line:R10",
                                    "publicCode": "R10",
                                    "name": "Drammen - Lillehammer",
                                    "transportMode": "rail"
                                }
                            },
                            "situations": []
                        }]
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
}
