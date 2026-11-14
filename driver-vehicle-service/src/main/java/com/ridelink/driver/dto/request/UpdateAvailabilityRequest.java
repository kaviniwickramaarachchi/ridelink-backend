package com.ridelink.driver.dto.request;

import com.ridelink.driver.entity.AvailabilityStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAvailabilityRequest(

        @NotNull(message = "Availability status is required")
        AvailabilityStatus availabilityStatus) {
}
