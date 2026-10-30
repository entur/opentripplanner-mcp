package org.entur.mcp.model;

/**
 * A place to route from or to. Coordinates are absent for a bare stop ID, and the stop ID is
 * absent for anything that is not a stop.
 */
public class Location {
    private String place;
    private Double latitude;
    private Double longitude;
    private String stopId;

    public Location(String place, double latitude, double longitude) {
        this(place, latitude, longitude, null);
    }

    public Location(String place, Double latitude, Double longitude, String stopId) {
        this.place = place;
        this.latitude = latitude;
        this.longitude = longitude;
        this.stopId = stopId;
    }

    /** A stop known only by its NSR ID; OTP resolves where it is. */
    public static Location ofStopId(String stopId) {
        return new Location(stopId, null, null, stopId);
    }

    public String getPlace() {
        return place;
    }

    public void setPlace(String place) {
        this.place = place;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getStopId() {
        return stopId;
    }

    public void setStopId(String stopId) {
        this.stopId = stopId;
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }
}
