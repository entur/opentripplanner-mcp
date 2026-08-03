package org.entur.mcp.rest;

import org.entur.mcp.exception.GeocodingException;
import org.entur.mcp.exception.TripPlanningException;
import org.entur.mcp.exception.ValidationException;
import org.entur.mcp.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps application exceptions to HTTP responses.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring's own MVC exceptions keep the status
 * they already carry — 404 for an unmapped path, 405 for a wrong method, and so on. Without it the
 * catch-all {@code @ExceptionHandler(Exception.class)} below intercepts them (they all extend
 * {@code Exception}) and rewrites every one into a 500.
 *
 * <p>That mattered beyond tidiness: MCP clients probe {@code /.well-known/oauth-protected-resource}
 * before connecting and read a 404 as "this server needs no auth". A 500 instead reads as a broken
 * server, sending the client into an OAuth flow this server cannot complete — so it never connects.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex) {
        log.warn("Validation error: {} - {}", ex.getField(), ex.getMessage());
        ErrorResponse error = ErrorResponse.validationError(ex.getField(), ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(error);
    }

    @ExceptionHandler(GeocodingException.class)
    public ResponseEntity<ErrorResponse> handleGeocodingException(GeocodingException ex) {
        log.warn("Geocoding error: {} - {}", ex.getLocation(), ex.getMessage());
        ErrorResponse error = ErrorResponse.geocodingError(ex.getLocation(), ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(error);
    }

    @ExceptionHandler(TripPlanningException.class)
    public ResponseEntity<ErrorResponse> handleTripPlanningException(TripPlanningException ex) {
        log.error("Trip planning error: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.tripPlanningError(ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error);
    }

    /**
     * Overrides the {@link ResponseEntityExceptionHandler} hook rather than declaring a competing
     * {@code @ExceptionHandler(MethodArgumentNotValidException.class)} — the parent already claims
     * that type in its {@code final} handler, and two handlers for it fail context startup with
     * "Ambiguous @ExceptionHandler method mapped".
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String field = ex.getBindingResult().getFieldError() != null
            ? ex.getBindingResult().getFieldError().getField()
            : "unknown";
        String message = ex.getBindingResult().getFieldError() != null
            ? ex.getBindingResult().getFieldError().getDefaultMessage()
            : "Validation failed";
        log.warn("Spring validation error: {} - {}", field, message);
        ErrorResponse error = ErrorResponse.validationError(field, message);
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        ErrorResponse error = ErrorResponse.genericError("An unexpected error occurred");
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error);
    }
}
