package org.entur.mcp.exception;

public class GeocodingException extends RuntimeException {
    private final String location;

    public GeocodingException(String location, String message) {
        super(message);
        this.location = location;
    }

    public GeocodingException(String location, String message, Throwable cause) {
        super(message, cause);
        this.location = location;
    }

    public String getLocation() {
        return location;
    }
}
