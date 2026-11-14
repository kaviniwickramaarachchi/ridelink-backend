package com.ridelink.driver.dto.request;

import com.ridelink.driver.entity.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateVehicleRequest(

        @NotBlank(message = "Registration number is required")
        @Size(
                max = 30,
                message = "Registration number must not exceed 30 characters")
        String registrationNumber,

        @NotBlank(message = "Vehicle make is required")
        @Size(
                max = 100,
                message = "Vehicle make must not exceed 100 characters")
        String make,

        @NotBlank(message = "Vehicle model is required")
        @Size(
                max = 100,
                message = "Vehicle model must not exceed 100 characters")
        String model,

        @NotNull(message = "Vehicle type is required")
        VehicleType vehicleType,

        @NotBlank(message = "Vehicle colour is required")
        @Size(
                max = 50,
                message = "Vehicle colour must not exceed 50 characters")
        String colour) {
}
