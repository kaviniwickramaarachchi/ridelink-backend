package com.ridelink.ride.client;

public class FareServiceIntegrationException extends RuntimeException {

    public FareServiceIntegrationException(String message) {
        super(message);
    }

    public FareServiceIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
