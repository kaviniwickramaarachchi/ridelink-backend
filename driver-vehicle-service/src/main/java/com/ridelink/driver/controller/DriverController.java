package com.ridelink.driver.controller;

import com.ridelink.driver.exception.InvalidRequestException;
import com.ridelink.driver.dto.request.CreateVehicleRequest;
import com.ridelink.driver.dto.request.UpdateAvailabilityRequest;
import com.ridelink.driver.dto.request.UpdateDriverProfileRequest;
import com.ridelink.driver.dto.request.UpdateLocationRequest;
import com.ridelink.driver.dto.request.UpdateVehicleRequest;
import com.ridelink.driver.dto.request.UpdateVehicleStatusRequest;
import com.ridelink.driver.dto.response.DriverProfileResponse;
import com.ridelink.driver.dto.response.VehicleResponse;
import com.ridelink.driver.service.DriverProfileService;
import com.ridelink.driver.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.ridelink.driver.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;


@Tag(
        name = "Driver and Vehicle Management",
        description = "JWT-protected operations for the authenticated driver")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {

    private final DriverProfileService driverProfileService;
    private final VehicleService vehicleService;

    public DriverController(
            DriverProfileService driverProfileService,
            VehicleService vehicleService) {
        this.driverProfileService = driverProfileService;
        this.vehicleService = vehicleService;
    }

    @PostMapping("/profile")
    @ResponseStatus(HttpStatus.CREATED)
    public DriverProfileResponse createProfile(
            @AuthenticationPrincipal Jwt jwt) {
        return driverProfileService.createProfile(
                extractAccountId(jwt));
    }

    @GetMapping("/me")
    public DriverProfileResponse getOwnProfile(
            @AuthenticationPrincipal Jwt jwt) {
        return driverProfileService.getOwnProfile(
                extractAccountId(jwt));
    }

    @PatchMapping("/me")
    public DriverProfileResponse updateOwnProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateDriverProfileRequest request) {
        return driverProfileService.updateOwnProfile(
                extractAccountId(jwt),
                request);
    }

    @PostMapping("/me/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleResponse addVehicle(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateVehicleRequest request) {
        return vehicleService.addVehicle(
                extractAccountId(jwt),
                request);
    }

    @GetMapping("/me/vehicle")
    public VehicleResponse getActiveVehicle(
            @AuthenticationPrincipal Jwt jwt) {
        return vehicleService.getActiveVehicle(
                extractAccountId(jwt));
    }

    @PatchMapping("/me/vehicle")
    public VehicleResponse updateActiveVehicle(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateVehicleRequest request) {
        return vehicleService.updateActiveVehicle(
                extractAccountId(jwt),
                request);
    }

    @PatchMapping("/me/vehicle/status")
    public VehicleResponse updateVehicleStatus(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateVehicleStatusRequest request) {
        return vehicleService.updateVehicleStatus(
                extractAccountId(jwt),
                request);
    }

    @PatchMapping("/me/availability")
    public DriverProfileResponse updateAvailability(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateAvailabilityRequest request) {
        return driverProfileService.updateAvailability(
                extractAccountId(jwt),
                request);
    }

    @PatchMapping("/me/location")
    public DriverProfileResponse updateLocation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateLocationRequest request) {
        return driverProfileService.updateLocation(
                extractAccountId(jwt),
                request);
    }

    private Long extractAccountId(Jwt jwt) {
        if (jwt == null) {
            throw new InvalidRequestException(
                    "Authenticated JWT is required");
        }

        Object userIdClaim = jwt.getClaim("userId");
        Long accountId = parseAccountId(userIdClaim);

        if (accountId == null || accountId <= 0) {
            throw new InvalidRequestException(
                    "JWT userId claim is missing or invalid");
        }

        return accountId;
    }

    private Long parseAccountId(Object userIdClaim) {
        if (userIdClaim instanceof Number number) {
            return number.longValue();
        }

        if (userIdClaim instanceof String value) {
            try {
                return Long.valueOf(value);
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        return null;
    }
}
