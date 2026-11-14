package com.ridelink.driver.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateDriverProfileRequest(

        @Pattern(
                regexp = ".*\\S.*",
                message = "Service area must not be blank")
        @Size(
                max = 100,
                message = "Service area must not exceed 100 characters")
        String serviceArea,

        @DecimalMin(
                value = "-90.0",
                message = "Latitude must be at least -90")
        @DecimalMax(
                value = "90.0",
                message = "Latitude must not exceed 90")
        @Digits(
                integer = 2,
                fraction = 6,
                message = "Latitude must contain at most 6 decimal places")
        BigDecimal currentLatitude,

        @DecimalMin(
                value = "-180.0",
                message = "Longitude must be at least -180")
        @DecimalMax(
                value = "180.0",
                message = "Longitude must not exceed 180")
        @Digits(
                integer = 3,
                fraction = 6,
                message = "Longitude must contain at most 6 decimal places")
        BigDecimal currentLongitude) {
}
