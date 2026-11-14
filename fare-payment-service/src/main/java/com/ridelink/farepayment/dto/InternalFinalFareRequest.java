package com.ridelink.farepayment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record InternalFinalFareRequest(
        @NotNull Long rideId,
        @NotNull Long passengerId,
        @NotNull @PositiveOrZero Double distanceKm,
        @NotNull @PositiveOrZero Double durationMinutes
) {
}