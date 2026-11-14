package com.ridelink.ride.client.dto;

public record InternalFinalFareResponse(
        Long rideId,
        Double distanceKm,
        Double durationMinutes,
        Double amount
) {
}
