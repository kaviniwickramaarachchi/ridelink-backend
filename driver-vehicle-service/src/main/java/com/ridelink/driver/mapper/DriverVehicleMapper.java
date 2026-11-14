package com.ridelink.driver.mapper;

import com.ridelink.driver.entity.DriverProfile;
import com.ridelink.driver.entity.Vehicle;
import com.ridelink.driver.dto.response.DriverIdResponse;
import com.ridelink.driver.dto.response.DriverProfileResponse;
import com.ridelink.driver.dto.response.EligibleDriverResponse;
import com.ridelink.driver.dto.response.VehicleResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class DriverVehicleMapper {

    public DriverProfileResponse toDriverProfileResponse(
            DriverProfile driverProfile) {
        return new DriverProfileResponse(
                driverProfile.getId(),
                driverProfile.getAccountId(),
                driverProfile.getAvailabilityStatus(),
                driverProfile.getServiceArea(),
                driverProfile.getCurrentLatitude(),
                driverProfile.getCurrentLongitude(),
                toInstant(driverProfile.getCreatedAt()),
                toInstant(driverProfile.getUpdatedAt()));
    }

    public VehicleResponse toVehicleResponse(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getDriver().getId(),
                vehicle.getRegistrationNumber(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getVehicleType(),
                vehicle.getColour(),
                vehicle.getVehicleStatus(),
                toInstant(vehicle.getCreatedAt()),
                toInstant(vehicle.getUpdatedAt()));
    }

    public EligibleDriverResponse toEligibleDriverResponse(
            DriverProfile driverProfile) {
        return new EligibleDriverResponse(
                driverProfile.getId(),
                driverProfile.getAccountId(),
                driverProfile.getServiceArea(),
                driverProfile.getCurrentLatitude(),
                driverProfile.getCurrentLongitude());
    }

    public DriverIdResponse toDriverIdResponse(
            DriverProfile driverProfile) {
        return new DriverIdResponse(
                driverProfile.getAccountId(),
                driverProfile.getId());
    }

    private Instant toInstant(LocalDateTime value) {
        if (value == null) {
            return null;
        }

        return value.toInstant(ZoneOffset.UTC);
    }
}
