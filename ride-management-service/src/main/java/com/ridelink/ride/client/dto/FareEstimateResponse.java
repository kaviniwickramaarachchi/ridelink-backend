package com.ridelink.ride.client.dto;

public record FareEstimateResponse(
        double distanceKm,
        double estimatedFare
) {
}
