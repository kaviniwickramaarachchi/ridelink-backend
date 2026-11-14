package com.ridelink.farepayment.dto;

public record FareEstimateResponse(
        double distanceKm,
        double estimatedFare
) {
}