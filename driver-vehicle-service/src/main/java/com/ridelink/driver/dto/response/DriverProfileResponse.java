package com.ridelink.driver.dto.response;

import com.ridelink.driver.entity.AvailabilityStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record DriverProfileResponse(
        Long id,
        Long accountId,
        AvailabilityStatus availabilityStatus,
        String serviceArea,
        BigDecimal currentLatitude,
        BigDecimal currentLongitude,
        Instant createdAt,
        Instant updatedAt) {
}
