package com.ridelink.ride.dto;

import com.ridelink.ride.domain.RideStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record RideResponse(
        Long id,
        Long passengerId,
        Long driverId,
        String serviceArea,
        LocationResponse pickup,
        LocationResponse destination,
        RideStatus status,
        Instant requestedAt,
        Instant assignedAt,
        Instant acceptedAt,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        BigDecimal actualDistance,
        Instant createdAt,
        Instant updatedAt
) {
}
