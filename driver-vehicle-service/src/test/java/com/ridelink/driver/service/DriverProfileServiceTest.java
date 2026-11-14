package com.ridelink.driver.service;

import com.ridelink.driver.entity.AvailabilityStatus;
import com.ridelink.driver.entity.DriverProfile;
import com.ridelink.driver.entity.VehicleStatus;
import com.ridelink.driver.exception.BusinessConflictException;
import com.ridelink.driver.mapper.DriverVehicleMapper;
import com.ridelink.driver.repository.DriverProfileRepository;
import com.ridelink.driver.repository.VehicleRepository;
import com.ridelink.driver.dto.request.UpdateAvailabilityRequest;
import com.ridelink.driver.dto.response.DriverProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverProfileServiceTest {

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    private DriverProfileService driverProfileService;

    @BeforeEach
    void setUp() {
        driverProfileService = new DriverProfileService(
                driverProfileRepository,
                vehicleRepository,
                new DriverVehicleMapper());
    }

    @Test
    void createProfileStartsAsUnavailable() {
        when(driverProfileRepository.existsByAccountId(25L))
                .thenReturn(false);

        when(driverProfileRepository.save(any(DriverProfile.class)))
                .thenAnswer(invocation -> {
                    DriverProfile profile = invocation.getArgument(0);
                    ReflectionTestUtils.setField(profile, "id", 10L);
                    return profile;
                });

        DriverProfileResponse response =
                driverProfileService.createProfile(25L);

        assertEquals(10L, response.id());
        assertEquals(25L, response.accountId());
        assertEquals(
                AvailabilityStatus.UNAVAILABLE,
                response.availabilityStatus());
    }

    @Test
    void createProfileRejectsDuplicateAccount() {
        when(driverProfileRepository.existsByAccountId(25L))
                .thenReturn(true);

        assertThrows(
                BusinessConflictException.class,
                () -> driverProfileService.createProfile(25L));

        verify(driverProfileRepository, never())
                .save(any(DriverProfile.class));
    }

    @Test
    void becomingAvailableRequiresActiveVehicle() {
        DriverProfile profile = completeProfile(
                10L,
                25L,
                AvailabilityStatus.UNAVAILABLE);

        when(driverProfileRepository.findByAccountId(25L))
                .thenReturn(Optional.of(profile));

        when(vehicleRepository.findByDriver_IdAndVehicleStatus(
                10L,
                VehicleStatus.ACTIVE))
                .thenReturn(Optional.empty());

        UpdateAvailabilityRequest request =
                new UpdateAvailabilityRequest(
                        AvailabilityStatus.AVAILABLE);

        assertThrows(
                BusinessConflictException.class,
                () -> driverProfileService.updateAvailability(
                        25L,
                        request));
    }

    @Test
    void eligibleLookupReturnsMatchingDrivers() {
        DriverProfile profile = completeProfile(
                10L,
                25L,
                AvailabilityStatus.AVAILABLE);

        when(driverProfileRepository.findEligibleDrivers(
                "Colombo",
                AvailabilityStatus.AVAILABLE,
                VehicleStatus.ACTIVE))
                .thenReturn(List.of(profile));

        var responses =
                driverProfileService.findEligibleDrivers("Colombo");

        assertEquals(1, responses.size());
        assertEquals(10L, responses.getFirst().driverId());
        assertEquals(25L, responses.getFirst().accountId());
    }

    @Test
    void reservationConflictIsReported() {
        DriverProfile profile = completeProfile(
                10L,
                25L,
                AvailabilityStatus.UNAVAILABLE);

        when(driverProfileRepository.findById(10L))
                .thenReturn(Optional.of(profile));

        when(driverProfileRepository.updateAvailabilityIfCurrent(
                10L,
                AvailabilityStatus.AVAILABLE,
                AvailabilityStatus.UNAVAILABLE))
                .thenReturn(0);

        assertThrows(
                BusinessConflictException.class,
                () -> driverProfileService.reserveDriver(10L));
    }

    @Test
    void availableDriverCanBeReservedAtomically() {
        DriverProfile availableProfile = completeProfile(
                10L,
                25L,
                AvailabilityStatus.AVAILABLE);

        DriverProfile reservedProfile = completeProfile(
                10L,
                25L,
                AvailabilityStatus.UNAVAILABLE);

        when(driverProfileRepository.findById(10L))
                .thenReturn(Optional.of(availableProfile))
                .thenReturn(Optional.of(reservedProfile));

        when(driverProfileRepository.updateAvailabilityIfCurrent(
                10L,
                AvailabilityStatus.AVAILABLE,
                AvailabilityStatus.UNAVAILABLE))
                .thenReturn(1);

        DriverProfileResponse response =
                driverProfileService.reserveDriver(10L);

        assertEquals(
                AvailabilityStatus.UNAVAILABLE,
                response.availabilityStatus());
    }

    private DriverProfile completeProfile(
            Long driverId,
            Long accountId,
            AvailabilityStatus availabilityStatus) {

        DriverProfile profile = new DriverProfile(accountId);
        ReflectionTestUtils.setField(profile, "id", driverId);

        profile.setServiceArea("Colombo");
        profile.setCurrentLatitude(new BigDecimal("6.927079"));
        profile.setCurrentLongitude(new BigDecimal("79.861244"));
        profile.setAvailabilityStatus(availabilityStatus);

        return profile;
    }
}
