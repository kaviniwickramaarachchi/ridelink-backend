package com.ridelink.ride.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRideRequest(
        @NotBlank @Size(max = 100) String serviceArea,
        @NotNull @Valid LocationRequest pickup,
        @NotNull @Valid LocationRequest destination
) {
}
