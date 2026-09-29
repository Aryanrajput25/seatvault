package com.aryanrajput.seatvault.booking.service;

/**
 * Thrown when a payment arrives for a booking whose seat hold has expired or
 * been taken over by another booking. Still maps to HTTP 409, but being a
 * separate type lets BookingService.succeed() commit the "expire this
 * booking" work it did before throwing.
 */
public class HoldExpiredException extends IllegalStateException {

    public HoldExpiredException(String message) {
        super(message);
    }
}
