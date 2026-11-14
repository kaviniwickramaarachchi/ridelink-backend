package com.ridelink.ride.client;

public class DriverServiceIntegrationException extends RuntimeException {

    public DriverServiceIntegrationException(String message) {
        super(message);
    }

    public DriverServiceIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
