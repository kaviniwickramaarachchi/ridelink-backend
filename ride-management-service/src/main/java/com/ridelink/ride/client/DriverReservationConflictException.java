package com.ridelink.ride.client;

public class DriverReservationConflictException extends RuntimeException {

    public DriverReservationConflictException(Long driverId) {
        super("Driver is no longer available: " + driverId);
    }
}
