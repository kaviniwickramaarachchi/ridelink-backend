package com.ridelink.ride.exception;

import com.ridelink.ride.domain.RideStatus;

public class InvalidRideTransitionException extends RuntimeException {

    public InvalidRideTransitionException(RideStatus currentStatus, RideStatus targetStatus) {
        super("Cannot transition ride from " + currentStatus + " to " + targetStatus);
    }
}
