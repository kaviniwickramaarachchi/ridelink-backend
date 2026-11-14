package com.ridelink.ride.service;

import com.ridelink.ride.client.DriverServiceClient;
import com.ridelink.ride.client.FareServiceClient;
import com.ridelink.ride.client.FareServiceIntegrationException;
import com.ridelink.ride.client.dto.EligibleDriverResponse;
import com.ridelink.ride.client.dto.FareEstimateResponse;
import com.ridelink.ride.domain.Ride;
import com.ridelink.ride.domain.RideLocation;
import com.ridelink.ride.domain.RideStatus;
import com.ridelink.ride.dto.CreateRideRequest;
import com.ridelink.ride.dto.LocationRequest;
import com.ridelink.ride.dto.RideResponse;
import com.ridelink.ride.repository.RideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RideServiceFareIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-29T04:00:00Z");
    private static final Long RIDE_ID = 101L;
    private static final Long PASSENGER_ID = 42L;
    private static final Long DRIVER_ID = 81L;

    @Mock
    private RideRepository rideRepository;

    @Mock
    private DriverServiceClient driverServiceClient;

    @Mock
    private FareServiceClient fareServiceClient;

    private RideService rideService;

    @BeforeEach
    void setUp() {
        rideService = new RideService(
                rideRepository,
                driverServiceClient,
                fareServiceClient,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createRideInvokesFareEstimateBeforeLocalPersistenceAndDriverLookup() {
        stubFareEstimate();
        stubCreateSave();
        when(driverServiceClient.findEligibleDrivers("Colombo")).thenReturn(List.of());

        rideService.createRide(PASSENGER_ID, request());

        InOrder order = inOrder(fareServiceClient, rideRepository, driverServiceClient);
        order.verify(fareServiceClient).estimateFare(any(RideLocation.class), any(RideLocation.class));
        order.verify(rideRepository).save(any(Ride.class));
        order.verify(driverServiceClient).findEligibleDrivers("Colombo");
    }

    @Test
    void createRideFareEstimateFailurePreventsRideSaveAndDriverLookup() {
        doThrow(new FareServiceIntegrationException("fare down"))
                .when(fareServiceClient).estimateFare(any(RideLocation.class), any(RideLocation.class));

        assertThrows(FareServiceIntegrationException.class,
                () -> rideService.createRide(PASSENGER_ID, request()));

        verify(rideRepository, never()).save(any(Ride.class));
        verify(driverServiceClient, never()).findEligibleDrivers(any());
        verify(driverServiceClient, never()).reserveDriver(any());
    }

    @Test
    void successfulFareEstimatePreservesDriverAssignmentBehavior() {
        stubFareEstimate();
        stubCreateSave();
        when(driverServiceClient.findEligibleDrivers("Colombo"))
                .thenReturn(List.of(candidate(DRIVER_ID, 901L)));

        RideResponse response = rideService.createRide(PASSENGER_ID, request());

        assertEquals(RideStatus.ASSIGNED, response.status());
        assertEquals(DRIVER_ID, response.driverId());
        verify(driverServiceClient).reserveDriver(DRIVER_ID);
    }

    @Test
    void completeRideTriggersFinalFareOnlyAfterSuccessfulTransactionCommitWithExpectedPayload() {
        Ride ride = inProgressRide();
        when(rideRepository.findByIdAndDriverId(RIDE_ID, DRIVER_ID)).thenReturn(Optional.of(ride));
        when(rideRepository.save(ride)).thenReturn(ride);
        TransactionSynchronizationManager.initSynchronization();
        try {
            RideResponse response = rideService.completeRide(
                    RIDE_ID, DRIVER_ID, new BigDecimal("12.345"));

            assertEquals(RideStatus.COMPLETED, response.status());
            verify(fareServiceClient, never()).createFinalFare(any(), any(), any(), any(Double.class));

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());

            ArgumentCaptor<BigDecimal> distanceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
            ArgumentCaptor<Double> durationCaptor = ArgumentCaptor.forClass(Double.class);
            verify(fareServiceClient).createFinalFare(
                    eq(RIDE_ID),
                    eq(PASSENGER_ID),
                    distanceCaptor.capture(),
                    durationCaptor.capture()
            );
            assertEquals(new BigDecimal("12.345"), distanceCaptor.getValue());
            assertEquals(1.0, durationCaptor.getValue());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void finalFareFailureDoesNotUndoCompletedRide() {
        Ride ride = inProgressRide();
        when(rideRepository.findByIdAndDriverId(RIDE_ID, DRIVER_ID)).thenReturn(Optional.of(ride));
        when(rideRepository.save(ride)).thenReturn(ride);
        doThrow(new FareServiceIntegrationException("fare down"))
                .when(fareServiceClient)
                .createFinalFare(eq(RIDE_ID), eq(PASSENGER_ID), any(BigDecimal.class), eq(1.0));

        RideResponse response = rideService.completeRide(
                RIDE_ID, DRIVER_ID, new BigDecimal("12.345"));

        assertEquals(RideStatus.COMPLETED, response.status());
        assertEquals(NOW, response.completedAt());
        verify(rideRepository).save(ride);
    }

    private void stubFareEstimate() {
        when(fareServiceClient.estimateFare(any(RideLocation.class), any(RideLocation.class)))
                .thenReturn(new FareEstimateResponse(4.75, 480.0));
    }

    private void stubCreateSave() {
        when(rideRepository.save(any(Ride.class))).thenAnswer(invocation -> {
            Ride ride = invocation.getArgument(0);
            ReflectionTestUtils.setField(ride, "id", RIDE_ID);
            return ride;
        });
    }

    private CreateRideRequest request() {
        return new CreateRideRequest(
                "Colombo",
                new LocationRequest("Colombo Fort", new BigDecimal("6.9344"), new BigDecimal("79.8428")),
                new LocationRequest("Bambalapitiya", new BigDecimal("6.8935"), new BigDecimal("79.8552"))
        );
    }

    private EligibleDriverResponse candidate(Long driverId, Long accountId) {
        return new EligibleDriverResponse(
                driverId,
                accountId,
                "Colombo",
                new BigDecimal("6.9344"),
                new BigDecimal("79.8428")
        );
    }

    private Ride inProgressRide() {
        Ride ride = Ride.create(
                PASSENGER_ID,
                "Colombo",
                new RideLocation("Colombo Fort", new BigDecimal("6.9344"), new BigDecimal("79.8428")),
                new RideLocation("Bambalapitiya", new BigDecimal("6.8935"), new BigDecimal("79.8552")),
                NOW.minusSeconds(240),
                NOW.minusSeconds(240),
                NOW.minusSeconds(240)
        );
        ReflectionTestUtils.setField(ride, "id", RIDE_ID);
        ride.assignDriver(DRIVER_ID, NOW.minusSeconds(180));
        ride.accept(NOW.minusSeconds(120));
        ride.start(NOW.minusSeconds(60));
        return ride;
    }
}
