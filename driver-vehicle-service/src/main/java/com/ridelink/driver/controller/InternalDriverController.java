package com.ridelink.driver.controller;

import com.ridelink.driver.entity.AvailabilityStatus;
import com.ridelink.driver.dto.request.UpdateAvailabilityRequest;
import com.ridelink.driver.dto.response.DriverIdResponse;
import com.ridelink.driver.dto.response.DriverProfileResponse;
import com.ridelink.driver.dto.response.EligibleDriverResponse;
import com.ridelink.driver.service.DriverProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ridelink.driver.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;


@Tag(
        name = "Internal Driver Operations",
        description = "Operations used by other RideLink services")
@SecurityRequirement(name = OpenApiConfig.INTERNAL_API_KEY)

@RestController
@RequestMapping("/internal/drivers")
@Validated
public class InternalDriverController {

    private final DriverProfileService driverProfileService;

    public InternalDriverController(
            DriverProfileService driverProfileService) {
        this.driverProfileService = driverProfileService;
    }

    @GetMapping("/eligible")
    public List<EligibleDriverResponse> findEligibleDrivers(
            @RequestParam String serviceArea) {
        return driverProfileService.findEligibleDrivers(serviceArea);
    }

    @GetMapping("/by-account/{accountId}")
    public DriverIdResponse resolveDriverIdByAccountId(
            @PathVariable
            @Positive(message = "Account ID must be a positive number")
            Long accountId) {
        return driverProfileService.resolveDriverIdByAccountId(
                accountId);
    }

    @PatchMapping("/{driverId}/availability")
    public DriverProfileResponse updateAvailabilityInternally(
            @PathVariable
            @Positive(message = "Driver ID must be a positive number")
            Long driverId,
            @Valid @RequestBody UpdateAvailabilityRequest request) {

        if (request.availabilityStatus()
                == AvailabilityStatus.UNAVAILABLE) {
            return driverProfileService.reserveDriver(driverId);
        }

        return driverProfileService.releaseDriver(driverId);
    }
}
