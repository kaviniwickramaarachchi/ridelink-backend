package com.ridelink.driver.dto.request;

import com.ridelink.driver.entity.VehicleStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateVehicleStatusRequest(

        @NotNull(message = "Vehicle status is required")
        VehicleStatus vehicleStatus) {
}
