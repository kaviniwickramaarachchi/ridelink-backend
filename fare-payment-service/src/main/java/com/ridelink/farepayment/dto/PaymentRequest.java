package com.ridelink.farepayment.dto;

import com.ridelink.farepayment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
        @NotNull Long rideId,
        @NotNull PaymentMethod method,
        String cardToken
) {
}