package com.ridelink.driver.dto.request;

import com.ridelink.driver.entity.VehicleType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateVehicleRequest(

        @Pattern(
                regexp = ".*\\S.*",
                message = "Registration number must not be blank")
        @Size(
                max = 30,
                message = "Registration number must not exceed 30 characters")
        String registrationNumber,

        @Pattern(
                regexp = ".*\\S.*",
                message = "Vehicle make must not be blank")
        @Size(
                max = 100,
                message = "Vehicle make must not exceed 100 characters")
        String make,

        @Pattern(
                regexp = ".*\\S.*",
                message = "Vehicle model must not be blank")
        @Size(
                max = 100,
                message = "Vehicle model must not exceed 100 characters")
        String model,

        VehicleType vehicleType,

        @Pattern(
                regexp = ".*\\S.*",
                message = "Vehicle colour must not be blank")
        @Size(
                max = 50,
                message = "Vehicle colour must not exceed 50 characters")
        String colour) {
}
