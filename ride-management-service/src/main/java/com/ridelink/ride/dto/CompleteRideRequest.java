package com.ridelink.ride.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CompleteRideRequest(
        @NotNull
        @DecimalMin(value = "0.001", inclusive = true)
        @Digits(integer = 7, fraction = 3)
        BigDecimal actualDistance
) {
}
