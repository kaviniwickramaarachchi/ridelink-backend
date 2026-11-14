package com.ridelink.driver.service;

import com.ridelink.driver.entity.AvailabilityStatus;
import com.ridelink.driver.entity.DriverProfile;
import com.ridelink.driver.entity.Vehicle;
import com.ridelink.driver.entity.VehicleStatus;
import com.ridelink.driver.exception.BusinessConflictException;
import com.ridelink.driver.exception.InvalidRequestException;
import com.ridelink.driver.exception.ResourceNotFoundException;
import com.ridelink.driver.mapper.DriverVehicleMapper;
import com.ridelink.driver.repository.DriverProfileRepository;
import com.ridelink.driver.repository.VehicleRepository;
import com.ridelink.driver.dto.request.CreateVehicleRequest;
import com.ridelink.driver.dto.request.UpdateVehicleRequest;
import com.ridelink.driver.dto.request.UpdateVehicleStatusRequest;
import com.ridelink.driver.dto.response.VehicleResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final DriverVehicleMapper mapper;

    public VehicleService(
            VehicleRepository vehicleRepository,
            DriverProfileRepository driverProfileRepository,
            DriverVehicleMapper mapper) {
        this.vehicleRepository = vehicleRepository;
        this.driverProfileRepository = driverProfileRepository;
        this.mapper = mapper;
    }

    @Transactional
    public VehicleResponse addVehicle(
            Long accountId,
            CreateVehicleRequest request) {

        DriverProfile driverProfile =
                findDriverByAccountIdForUpdate(accountId);

        String registrationNumber =
                normalizeRegistrationNumber(
                        request.registrationNumber());

        if (vehicleRepository
                .existsByRegistrationNumberIgnoreCase(
                        registrationNumber)) {
            throw new BusinessConflictException(
                    "A vehicle with this registration number already exists");
        }

        vehicleRepository.deactivateActiveVehicles(
                driverProfile.getId(),
                VehicleStatus.ACTIVE,
                VehicleStatus.INACTIVE);

        DriverProfile managedDriver =
                driverProfileRepository.findById(driverProfile.getId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Driver profile was not found"));

        Vehicle vehicle = new Vehicle(
                managedDriver,
                registrationNumber,
                request.make().trim(),
                request.model().trim(),
                request.vehicleType(),
                request.colour().trim());

        try {
            return mapper.toVehicleResponse(
                    vehicleRepository.save(vehicle));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessConflictException(
                    "A vehicle with this registration number already exists");
        }
    }

    @Transactional(readOnly = true)
    public VehicleResponse getActiveVehicle(Long accountId) {
        DriverProfile driverProfile =
                findDriverByAccountId(accountId);

        return mapper.toVehicleResponse(
                findActiveVehicle(driverProfile.getId()));
    }

    @Transactional
    public VehicleResponse updateActiveVehicle(
            Long accountId,
            UpdateVehicleRequest request) {

        DriverProfile driverProfile =
                findDriverByAccountId(accountId);

        Vehicle vehicle =
                findActiveVehicle(driverProfile.getId());

        boolean changed = false;

        if (request.registrationNumber() != null) {
            String registrationNumber =
                    normalizeRegistrationNumber(
                            request.registrationNumber());

            boolean registrationAlreadyExists =
                    vehicleRepository
                            .existsByRegistrationNumberIgnoreCaseAndIdNot(
                                    registrationNumber,
                                    vehicle.getId());

            if (registrationAlreadyExists) {
                throw new BusinessConflictException(
                        "A vehicle with this registration number already exists");
            }

            vehicle.setRegistrationNumber(registrationNumber);
            changed = true;
        }

        if (request.make() != null) {
            vehicle.setMake(request.make().trim());
            changed = true;
        }

        if (request.model() != null) {
            vehicle.setModel(request.model().trim());
            changed = true;
        }

        if (request.vehicleType() != null) {
            vehicle.setVehicleType(request.vehicleType());
            changed = true;
        }

        if (request.colour() != null) {
            vehicle.setColour(request.colour().trim());
            changed = true;
        }

        if (!changed) {
            throw new InvalidRequestException(
                    "At least one vehicle field must be provided");
        }

        try {
            return mapper.toVehicleResponse(
                    vehicleRepository.save(vehicle));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessConflictException(
                    "A vehicle with this registration number already exists");
        }
    }

    @Transactional
    public VehicleResponse updateVehicleStatus(
            Long accountId,
            UpdateVehicleStatusRequest request) {

        DriverProfile driverProfile =
                findDriverByAccountIdForUpdate(accountId);

        if (request.vehicleStatus() == VehicleStatus.INACTIVE) {
            return deactivateCurrentVehicle(driverProfile);
        }

        return activateMostRecentVehicle(driverProfile);
    }

    private VehicleResponse deactivateCurrentVehicle(
            DriverProfile driverProfile) {

        Vehicle activeVehicle =
                findActiveVehicle(driverProfile.getId());

        activeVehicle.setVehicleStatus(VehicleStatus.INACTIVE);

        if (driverProfile.getAvailabilityStatus()
                == AvailabilityStatus.AVAILABLE) {
            driverProfile.setAvailabilityStatus(
                    AvailabilityStatus.UNAVAILABLE);
            driverProfileRepository.save(driverProfile);
        }

        return mapper.toVehicleResponse(
                vehicleRepository.save(activeVehicle));
    }

    private VehicleResponse activateMostRecentVehicle(
            DriverProfile driverProfile) {

        var existingActiveVehicle =
                vehicleRepository.findByDriver_IdAndVehicleStatus(
                        driverProfile.getId(),
                        VehicleStatus.ACTIVE);

        if (existingActiveVehicle.isPresent()) {
            return mapper.toVehicleResponse(
                    existingActiveVehicle.get());
        }

        List<Vehicle> vehicles =
                vehicleRepository
                        .findAllByDriver_IdOrderByCreatedAtDesc(
                                driverProfile.getId());

        if (vehicles.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No vehicle was found for this driver");
        }

        Vehicle mostRecentVehicle = vehicles.getFirst();

        vehicleRepository.deactivateActiveVehicles(
                driverProfile.getId(),
                VehicleStatus.ACTIVE,
                VehicleStatus.INACTIVE);

        Vehicle managedVehicle =
                vehicleRepository.findById(
                                mostRecentVehicle.getId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Vehicle was not found"));

        managedVehicle.setVehicleStatus(VehicleStatus.ACTIVE);

        return mapper.toVehicleResponse(
                vehicleRepository.save(managedVehicle));
    }

    private DriverProfile findDriverByAccountId(Long accountId) {
        validateAccountId(accountId);

        return driverProfileRepository.findByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Driver profile was not found"));
    }

    private DriverProfile findDriverByAccountIdForUpdate(
            Long accountId) {
        validateAccountId(accountId);

        return driverProfileRepository
                .findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Driver profile was not found"));
    }

    private void validateAccountId(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidRequestException(
                    "Account ID must be a positive number");
        }
    }

    private Vehicle findActiveVehicle(Long driverId) {
        return vehicleRepository
                .findByDriver_IdAndVehicleStatus(
                        driverId,
                        VehicleStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "An active vehicle was not found"));
    }

    private String normalizeRegistrationNumber(
            String registrationNumber) {
        return registrationNumber
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}
