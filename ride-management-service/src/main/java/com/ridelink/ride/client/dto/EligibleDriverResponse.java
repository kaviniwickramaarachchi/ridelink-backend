package com.ridelink.ride.client.dto;

import java.math.BigDecimal;

public record EligibleDriverResponse(
        Long driverId,
        Long accountId,
        String serviceArea,
        BigDecimal currentLatitude,
        BigDecimal currentLongitude
) {
}
