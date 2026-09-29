package com.aryanrajput.seatvault.booking.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Translates the plain Java exceptions thrown by the service layer into HTTP
 * status codes, so controllers don't need any try/catch of their own.
 *
 * <pre>
 *   NoSuchElementException            -&gt; 404 Not Found
 *   IllegalArgumentException          -&gt; 400 Bad Request   (invalid input)
 *   SecurityException                 -&gt; 400 Bad Request   (wrong owner)
 *   IllegalStateException             -&gt; 409 Conflict      (seat unavailable, hold expired, etc.)
 * </pre>
 */
@RestControllerAdvice //@RestControllerAdvice provides centralized exception handling for REST controllers. It allows application exceptions to be mapped consistently to appropriate HTTP responses.
public class ApiExceptionHandler { //This handles exceptions thrown by the application and converts them into proper HTTP responses.

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(Exception exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, SecurityException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(Exception exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleConflict(Exception exception) {
        return Map.of("error", exception.getMessage());
    }
}
