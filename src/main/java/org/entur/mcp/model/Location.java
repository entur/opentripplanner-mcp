package org.entur.mcp.model;

public class Location {
    private String place;
    private double latitude;
    private double longitude;
    
    public Location(String place, double latitude, double longitude) {
        this.place = place;
        this.latitude = latitude;
        this.longitude = longitude;
    }
    
    public String getPlace() {
        return place;
    }
    
    public void setPlace(String place) {
        this.place = place;
    }
    
    public double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
}