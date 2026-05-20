package org.entur.mcp.exception;

public class MobilityException extends RuntimeException {

    public MobilityException(String message) {
        super(message);
    }

    public MobilityException(String message, Throwable cause) {
        super(message, cause);
    }
}