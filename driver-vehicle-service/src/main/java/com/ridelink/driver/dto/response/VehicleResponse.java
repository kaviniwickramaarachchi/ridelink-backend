package com.ridelink.driver.dto.response;

import com.ridelink.driver.entity.VehicleStatus;
import com.ridelink.driver.entity.VehicleType;

import java.time.Instant;

public record VehicleResponse(
        Long id,
        Long driverId,
        String registrationNumber,
        String make,
        String model,
        VehicleType vehicleType,
        String colour,
        VehicleStatus vehicleStatus,
        Instant createdAt,
        Instant updatedAt) {
}
