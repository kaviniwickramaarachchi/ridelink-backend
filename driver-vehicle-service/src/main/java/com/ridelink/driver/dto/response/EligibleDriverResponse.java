package com.ridelink.driver.dto.response;

import java.math.BigDecimal;

public record EligibleDriverResponse(
        Long driverId,
        Long accountId,
        String serviceArea,
        BigDecimal currentLatitude,
        BigDecimal currentLongitude) {
}
