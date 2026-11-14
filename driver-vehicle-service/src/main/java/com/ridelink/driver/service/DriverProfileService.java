package com.ridelink.driver.service;

import com.ridelink.driver.entity.AvailabilityStatus;
import com.ridelink.driver.entity.DriverProfile;
import com.ridelink.driver.entity.VehicleStatus;
import com.ridelink.driver.exception.BusinessConflictException;
import com.ridelink.driver.exception.InvalidRequestException;
import com.ridelink.driver.exception.ResourceNotFoundException;
import com.ridelink.driver.mapper.DriverVehicleMapper;
import com.ridelink.driver.repository.DriverProfileRepository;
import com.ridelink.driver.repository.VehicleRepository;
import com.ridelink.driver.dto.request.UpdateAvailabilityRequest;
import com.ridelink.driver.dto.request.UpdateDriverProfileRequest;
import com.ridelink.driver.dto.request.UpdateLocationRequest;
import com.ridelink.driver.dto.response.DriverIdResponse;
import com.ridelink.driver.dto.response.DriverProfileResponse;
import com.ridelink.driver.dto.response.EligibleDriverResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DriverProfileService {

    private final DriverProfileRepository driverProfileRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverVehicleMapper mapper;

    public DriverProfileService(
            DriverProfileRepository driverProfileRepository,
            VehicleRepository vehicleRepository,
            DriverVehicleMapper mapper) {
        this.driverProfileRepository = driverProfileRepository;
        this.vehicleRepository = vehicleRepository;
        this.mapper = mapper;
    }

    @Transactional
    public DriverProfileResponse createProfile(Long accountId) {
        validateAccountId(accountId);

        if (driverProfileRepository.existsByAccountId(accountId)) {
            throw new BusinessConflictException(
                    "A driver profile already exists for this account");
        }

        DriverProfile driverProfile = new DriverProfile(accountId);

        try {
            DriverProfile savedProfile =
                    driverProfileRepository.save(driverProfile);
            return mapper.toDriverProfileResponse(savedProfile);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessConflictException(
                    "A driver profile already exists for this account");
        }
    }

    @Transactional(readOnly = true)
    public DriverProfileResponse getOwnProfile(Long accountId) {
        return mapper.toDriverProfileResponse(
                findByAccountId(accountId));
    }

    @Transactional
    public DriverProfileResponse updateOwnProfile(
            Long accountId,
            UpdateDriverProfileRequest request) {

        DriverProfile driverProfile = findByAccountId(accountId);
        boolean changed = false;

        if (request.serviceArea() != null) {
            driverProfile.setServiceArea(request.serviceArea().trim());
            changed = true;
        }

        boolean latitudeProvided = request.currentLatitude() != null;
        boolean longitudeProvided = request.currentLongitude() != null;

        if (latitudeProvided != longitudeProvided) {
            throw new InvalidRequestException(
                    "Latitude and longitude must be provided together");
        }

        if (latitudeProvided) {
            driverProfile.setCurrentLatitude(
                    request.currentLatitude());
            driverProfile.setCurrentLongitude(
                    request.currentLongitude());
            changed = true;
        }

        if (!changed) {
            throw new InvalidRequestException(
                    "At least one profile field must be provided");
        }

        return mapper.toDriverProfileResponse(
                driverProfileRepository.save(driverProfile));
    }

    @Transactional
    public DriverProfileResponse updateLocation(
            Long accountId,
            UpdateLocationRequest request) {

        DriverProfile driverProfile = findByAccountId(accountId);

        driverProfile.setCurrentLatitude(request.latitude());
        driverProfile.setCurrentLongitude(request.longitude());

        return mapper.toDriverProfileResponse(
                driverProfileRepository.save(driverProfile));
    }

    @Transactional
    public DriverProfileResponse updateAvailability(
            Long accountId,
            UpdateAvailabilityRequest request) {

        DriverProfile driverProfile = findByAccountId(accountId);
        AvailabilityStatus requestedStatus =
                request.availabilityStatus();

        if (requestedStatus == driverProfile.getAvailabilityStatus()) {
            return mapper.toDriverProfileResponse(driverProfile);
        }

        if (requestedStatus == AvailabilityStatus.AVAILABLE) {
            validateAvailabilityPrerequisites(driverProfile);
        }

        driverProfile.setAvailabilityStatus(requestedStatus);

        return mapper.toDriverProfileResponse(
                driverProfileRepository.save(driverProfile));
    }

    @Transactional(readOnly = true)
    public List<EligibleDriverResponse> findEligibleDrivers(
            String serviceArea) {

        if (serviceArea == null || serviceArea.isBlank()) {
            throw new InvalidRequestException(
                    "Service area is required");
        }

        return driverProfileRepository.findEligibleDrivers(
                        serviceArea.trim(),
                        AvailabilityStatus.AVAILABLE,
                        VehicleStatus.ACTIVE)
                .stream()
                .map(mapper::toEligibleDriverResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DriverIdResponse resolveDriverIdByAccountId(
            Long accountId) {
        validateAccountId(accountId);

        return mapper.toDriverIdResponse(
                findByAccountId(accountId));
    }

    @Transactional
    public DriverProfileResponse reserveDriver(Long driverId) {
        findById(driverId);

        int updatedRows =
                driverProfileRepository.updateAvailabilityIfCurrent(
                        driverId,
                        AvailabilityStatus.AVAILABLE,
                        AvailabilityStatus.UNAVAILABLE);

        if (updatedRows != 1) {
            throw new BusinessConflictException(
                    "Driver is no longer available");
        }

        return mapper.toDriverProfileResponse(findById(driverId));
    }

    @Transactional
    public DriverProfileResponse releaseDriver(Long driverId) {
        DriverProfile driverProfile = findById(driverId);

        if (driverProfile.getAvailabilityStatus()
                == AvailabilityStatus.AVAILABLE) {
            return mapper.toDriverProfileResponse(driverProfile);
        }

        validateAvailabilityPrerequisites(driverProfile);

        int updatedRows =
                driverProfileRepository.updateAvailabilityIfCurrent(
                        driverId,
                        AvailabilityStatus.UNAVAILABLE,
                        AvailabilityStatus.AVAILABLE);

        if (updatedRows != 1) {
            DriverProfile currentProfile = findById(driverId);

            if (currentProfile.getAvailabilityStatus()
                    == AvailabilityStatus.AVAILABLE) {
                return mapper.toDriverProfileResponse(currentProfile);
            }

            throw new BusinessConflictException(
                    "Driver availability could not be released");
        }

        return mapper.toDriverProfileResponse(findById(driverId));
    }

    @Transactional(readOnly = true)
    public DriverProfile findEntityByAccountId(Long accountId) {
        return findByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public DriverProfile findEntityById(Long driverId) {
        return findById(driverId);
    }

    private DriverProfile findByAccountId(Long accountId) {
        validateAccountId(accountId);

        return driverProfileRepository.findByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Driver profile was not found"));
    }

    private DriverProfile findById(Long driverId) {
        if (driverId == null || driverId <= 0) {
            throw new InvalidRequestException(
                    "Driver ID must be a positive number");
        }

        return driverProfileRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Driver profile was not found"));
    }

    private void validateAvailabilityPrerequisites(
            DriverProfile driverProfile) {

        if (driverProfile.getServiceArea() == null
                || driverProfile.getServiceArea().isBlank()) {
            throw new BusinessConflictException(
                    "A service area is required before becoming available");
        }

        if (driverProfile.getCurrentLatitude() == null
                || driverProfile.getCurrentLongitude() == null) {
            throw new BusinessConflictException(
                    "A current location is required before becoming available");
        }

        boolean hasActiveVehicle =
                vehicleRepository.findByDriver_IdAndVehicleStatus(
                                driverProfile.getId(),
                                VehicleStatus.ACTIVE)
                        .isPresent();

        if (!hasActiveVehicle) {
            throw new BusinessConflictException(
                    "An active vehicle is required before becoming available");
        }
    }

    private void validateAccountId(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidRequestException(
                    "Account ID must be a positive number");
        }
    }
}
