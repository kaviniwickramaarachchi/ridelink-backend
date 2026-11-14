package com.ridelink.ride.client.dto;

public record InternalFinalFareRequest(
        Long rideId,
        Long passengerId,
        Double distanceKm,
        Double durationMinutes
) {
}
