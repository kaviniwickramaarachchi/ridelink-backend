package com.ridelink.farepayment.dto;

public record InternalFinalFareResponse(
        Long rideId,
        Double distanceKm,
        Double durationMinutes,
        Double amount
) {
}