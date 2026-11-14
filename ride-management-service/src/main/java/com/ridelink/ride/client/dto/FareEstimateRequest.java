package com.ridelink.ride.client.dto;

public record FareEstimateRequest(
        Double pickupLatitude,
        Double pickupLongitude,
        Double destinationLatitude,
        Double destinationLongitude
) {
}
