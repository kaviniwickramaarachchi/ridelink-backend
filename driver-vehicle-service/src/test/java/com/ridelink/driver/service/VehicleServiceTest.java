package com.ridelink.driver.service;

import com.ridelink.driver.entity.AvailabilityStatus;
import com.ridelink.driver.entity.DriverProfile;
import com.ridelink.driver.entity.Vehicle;
import com.ridelink.driver.entity.VehicleStatus;
import com.ridelink.driver.entity.VehicleType;
import com.ridelink.driver.exception.BusinessConflictException;
import com.ridelink.driver.exception.InvalidRequestException;
import com.ridelink.driver.mapper.DriverVehicleMapper;
import com.ridelink.driver.repository.DriverProfileRepository;
import com.ridelink.driver.repository.VehicleRepository;
import com.ridelink.driver.dto.request.CreateVehicleRequest;
import com.ridelink.driver.dto.request.UpdateVehicleRequest;
import com.ridelink.driver.dto.request.UpdateVehicleStatusRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    private VehicleService vehicleService;

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleService(
                vehicleRepository,
                driverProfileRepository,
                new DriverVehicleMapper());
    }

    @Test
    void addingVehicleDeactivatesPreviousActiveVehicle() {
        DriverProfile driver = driverProfile();

        when(driverProfileRepository.findByAccountIdForUpdate(25L))
                .thenReturn(Optional.of(driver));

        when(vehicleRepository
                .existsByRegistrationNumberIgnoreCase("WP CAB-1234"))
                .thenReturn(false);

        when(driverProfileRepository.findById(10L))
                .thenReturn(Optional.of(driver));

        when(vehicleRepository.save(any(Vehicle.class)))
                .thenAnswer(invocation -> {
                    Vehicle vehicle = invocation.getArgument(0);
                    ReflectionTestUtils.setField(vehicle, "id", 20L);
                    return vehicle;
                });

        CreateVehicleRequest request =
                new CreateVehicleRequest(
                        "wp cab-1234",
                        "Toyota",
                        "Prius",
                        VehicleType.CAR,
                        "White");

        var response = vehicleService.addVehicle(25L, request);

        assertEquals("WP CAB-1234", response.registrationNumber());
        assertEquals(VehicleStatus.ACTIVE, response.vehicleStatus());

        verify(vehicleRepository).deactivateActiveVehicles(
                10L,
                VehicleStatus.ACTIVE,
                VehicleStatus.INACTIVE);
        verify(driverProfileRepository)
                .findByAccountIdForUpdate(25L);
    }

    @Test
    void duplicateRegistrationNumberIsRejected() {
        DriverProfile driver = driverProfile();

        when(driverProfileRepository.findByAccountIdForUpdate(25L))
                .thenReturn(Optional.of(driver));

        when(vehicleRepository
                .existsByRegistrationNumberIgnoreCase("WP CAB-1234"))
                .thenReturn(true);

        CreateVehicleRequest request =
                new CreateVehicleRequest(
                        "WP CAB-1234",
                        "Toyota",
                        "Prius",
                        VehicleType.CAR,
                        "White");

        assertThrows(
                BusinessConflictException.class,
                () -> vehicleService.addVehicle(25L, request));

        verify(vehicleRepository, never())
                .save(any(Vehicle.class));
    }

    @Test
    void deactivatingVehicleMakesDriverUnavailable() {
        DriverProfile driver = driverProfile();
        driver.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);

        Vehicle vehicle = activeVehicle(driver);

        when(driverProfileRepository.findByAccountIdForUpdate(25L))
                .thenReturn(Optional.of(driver));

        when(vehicleRepository.findByDriver_IdAndVehicleStatus(
                10L,
                VehicleStatus.ACTIVE))
                .thenReturn(Optional.of(vehicle));

        when(vehicleRepository.save(vehicle))
                .thenReturn(vehicle);

        UpdateVehicleStatusRequest request =
                new UpdateVehicleStatusRequest(
                        VehicleStatus.INACTIVE);

        var response =
                vehicleService.updateVehicleStatus(25L, request);

        assertEquals(VehicleStatus.INACTIVE, response.vehicleStatus());
        assertEquals(
                AvailabilityStatus.UNAVAILABLE,
                driver.getAvailabilityStatus());

        verify(driverProfileRepository).save(driver);
    }

    @Test
    void emptyVehicleUpdateIsRejected() {
        DriverProfile driver = driverProfile();
        Vehicle vehicle = activeVehicle(driver);

        when(driverProfileRepository.findByAccountId(25L))
                .thenReturn(Optional.of(driver));

        when(vehicleRepository.findByDriver_IdAndVehicleStatus(
                10L,
                VehicleStatus.ACTIVE))
                .thenReturn(Optional.of(vehicle));

        UpdateVehicleRequest request =
                new UpdateVehicleRequest(
                        null,
                        null,
                        null,
                        null,
                        null);

        assertThrows(
                InvalidRequestException.class,
                () -> vehicleService.updateActiveVehicle(
                        25L,
                        request));
    }

    @Test
    void mostRecentVehicleCanBeReactivated() {
        DriverProfile driver = driverProfile();
        Vehicle vehicle = activeVehicle(driver);
        vehicle.setVehicleStatus(VehicleStatus.INACTIVE);

        when(driverProfileRepository.findByAccountIdForUpdate(25L))
                .thenReturn(Optional.of(driver));

        when(vehicleRepository.findByDriver_IdAndVehicleStatus(
                10L,
                VehicleStatus.ACTIVE))
                .thenReturn(Optional.empty());

        when(vehicleRepository
                .findAllByDriver_IdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(vehicle));

        when(vehicleRepository.findById(20L))
                .thenReturn(Optional.of(vehicle));

        when(vehicleRepository.save(vehicle))
                .thenReturn(vehicle);

        UpdateVehicleStatusRequest request =
                new UpdateVehicleStatusRequest(
                        VehicleStatus.ACTIVE);

        var response =
                vehicleService.updateVehicleStatus(25L, request);

        assertEquals(VehicleStatus.ACTIVE, response.vehicleStatus());
        verify(driverProfileRepository)
                .findByAccountIdForUpdate(25L);
    }

    private DriverProfile driverProfile() {
        DriverProfile driver = new DriverProfile(25L);
        ReflectionTestUtils.setField(driver, "id", 10L);
        return driver;
    }

    private Vehicle activeVehicle(DriverProfile driver) {
        Vehicle vehicle = new Vehicle(
                driver,
                "WP CAB-1234",
                "Toyota",
                "Prius",
                VehicleType.CAR,
                "White");

        ReflectionTestUtils.setField(vehicle, "id", 20L);
        return vehicle;
    }
}
