package com.ridelink.ride.exception;

public class RideNotFoundException extends RuntimeException {

    public RideNotFoundException(Long rideId) {
        super("Ride not found or inaccessible: " + rideId);
    }
}
