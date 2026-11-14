package com.ridelink.ride.service;

import com.ridelink.ride.client.DriverReservationConflictException;
import com.ridelink.ride.client.DriverServiceClient;
import com.ridelink.ride.client.DriverServiceIntegrationException;
import com.ridelink.ride.client.FareServiceClient;
import com.ridelink.ride.client.dto.FareEstimateResponse;
import com.ridelink.ride.client.dto.EligibleDriverResponse;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RideServiceDriverIntegrationTest {

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
        lenient().when(fareServiceClient.estimateFare(any(RideLocation.class), any(RideLocation.class)))
                .thenReturn(new FareEstimateResponse(4.75, 480.0));
    }

    @Test
    void emptyEligibleListLeavesRideRequested() {
        stubCreateSave();
        when(driverServiceClient.findEligibleDrivers("Colombo")).thenReturn(List.of());

        RideResponse response = rideService.createRide(PASSENGER_ID, request());

        assertEquals(RideStatus.REQUESTED, response.status());
        verify(driverServiceClient, never()).reserveDriver(any());
        verify(rideRepository).save(any(Ride.class));
    }

    @Test
    void firstCandidateReservationAssignsRide() {
        stubCreateSave();
        when(driverServiceClient.findEligibleDrivers("Colombo"))
                .thenReturn(List.of(candidate(DRIVER_ID, 901L)));

        RideResponse response = rideService.createRide(PASSENGER_ID, request());

        assertEquals(RideStatus.ASSIGNED, response.status());
        assertEquals(DRIVER_ID, response.driverId());
        verify(driverServiceClient).reserveDriver(DRIVER_ID);
        verify(rideRepository, times(2)).save(any(Ride.class));
    }

    @Test
    void reservationConflictTriesNextCandidateInOrder() {
        stubCreateSave();
        when(driverServiceClient.findEligibleDrivers("Colombo"))
                .thenReturn(List.of(candidate(81L, 901L), candidate(82L, 902L)));
        doThrow(new DriverReservationConflictException(81L))
                .when(driverServiceClient).reserveDriver(81L);

        RideResponse response = rideService.createRide(PASSENGER_ID, request());

        assertEquals(82L, response.driverId());
        InOrder order = inOrder(driverServiceClient);
        order.verify(driverServiceClient).findEligibleDrivers("Colombo");
        order.verify(driverServiceClient).reserveDriver(81L);
        order.verify(driverServiceClient).reserveDriver(82L);
    }

    @Test
    void allReservationConflictsLeaveRideRequested() {
        stubCreateSave();
        when(driverServiceClient.findEligibleDrivers("Colombo"))
                .thenReturn(List.of(candidate(81L, 901L), candidate(82L, 902L)));
        doThrow(new DriverReservationConflictException(81L))
                .when(driverServiceClient).reserveDriver(81L);
        doThrow(new DriverReservationConflictException(82L))
                .when(driverServiceClient).reserveDriver(82L);

        RideResponse response = rideService.createRide(PASSENGER_ID, request());

        assertEquals(RideStatus.REQUESTED, response.status());
        verify(rideRepository).save(any(Ride.class));
    }

    @Test
    void eligibilityFailureLeavesRideRequested() {
        stubCreateSave();
        when(driverServiceClient.findEligibleDrivers("Colombo"))
                .thenThrow(new DriverServiceIntegrationException("unavailable"));

        RideResponse response = rideService.createRide(PASSENGER_ID, request());

        assertEquals(RideStatus.REQUESTED, response.status());
        verify(driverServiceClient, never()).reserveDriver(any());
    }

    @Test
    void reservationIntegrationFailureLeavesRideRequested() {
        stubCreateSave();
        when(driverServiceClient.findEligibleDrivers("Colombo"))
                .thenReturn(List.of(candidate(DRIVER_ID, 901L)));
        doThrow(new DriverServiceIntegrationException("unavailable"))
                .when(driverServiceClient).reserveDriver(DRIVER_ID);

        RideResponse response = rideService.createRide(PASSENGER_ID, request());

        assertEquals(RideStatus.REQUESTED, response.status());
        verify(rideRepository).save(any(Ride.class));
    }

    @Test
    void assignmentUsesDriverIdNotAccountId() {
        stubCreateSave();
        when(driverServiceClient.findEligibleDrivers("Colombo"))
                .thenReturn(List.of(candidate(DRIVER_ID, 901L)));

        RideResponse response = rideService.createRide(PASSENGER_ID, request());

        assertEquals(DRIVER_ID, response.driverId());
        verify(driverServiceClient).reserveDriver(DRIVER_ID);
        verify(driverServiceClient, never()).reserveDriver(901L);
    }

    @Test
    void localAssignmentSaveFailureReleasesDriverAndPreservesException() {
        when(rideRepository.save(any(Ride.class)))
                .thenAnswer(invocation -> {
                    Ride ride = invocation.getArgument(0);
                    ReflectionTestUtils.setField(ride, "id", RIDE_ID);
                    return ride;
                })
                .thenThrow(new IllegalStateException("save failed"));
        when(driverServiceClient.findEligibleDrivers("Colombo"))
                .thenReturn(List.of(candidate(DRIVER_ID, 901L)));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> rideService.createRide(PASSENGER_ID, request())
        );

        assertEquals("save failed", exception.getMessage());
        verify(driverServiceClient).releaseDriver(DRIVER_ID);
    }

    @Test
    void completionRegistersReleaseOnlyAfterCommit() {
        Ride ride = inProgressRide();
        when(rideRepository.findByIdAndDriverId(RIDE_ID, DRIVER_ID)).thenReturn(Optional.of(ride));
        when(rideRepository.save(ride)).thenReturn(ride);
        TransactionSynchronizationManager.initSynchronization();
        try {
            RideResponse response = rideService.completeRide(
                    RIDE_ID, DRIVER_ID, new BigDecimal("12.345"));

            assertEquals(RideStatus.COMPLETED, response.status());
            verify(driverServiceClient, never()).releaseDriver(any());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(driverServiceClient).releaseDriver(DRIVER_ID);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void assignedAndAcceptedCancellationReleaseDriverAfterSave() {
        Ride assigned = assignedRide();
        when(rideRepository.findByIdAndPassengerId(RIDE_ID, PASSENGER_ID))
                .thenReturn(Optional.of(assigned));
        when(rideRepository.save(assigned)).thenReturn(assigned);

        rideService.cancelRide(RIDE_ID, PASSENGER_ID);

        InOrder assignedOrder = inOrder(rideRepository, driverServiceClient);
        assignedOrder.verify(rideRepository).save(assigned);
        assignedOrder.verify(driverServiceClient).releaseDriver(DRIVER_ID);

        Ride accepted = assignedRide();
        accepted.accept(NOW.minusSeconds(60));
        when(rideRepository.findByIdAndPassengerId(RIDE_ID, PASSENGER_ID))
                .thenReturn(Optional.of(accepted));
        when(rideRepository.save(accepted)).thenReturn(accepted);

        rideService.cancelRide(RIDE_ID, PASSENGER_ID);

        verify(driverServiceClient, times(2)).releaseDriver(DRIVER_ID);
    }

    @Test
    void requestedCancellationDoesNotReleaseDriver() {
        Ride requested = requestedRide();
        when(rideRepository.findByIdAndPassengerId(RIDE_ID, PASSENGER_ID))
                .thenReturn(Optional.of(requested));
        when(rideRepository.save(requested)).thenReturn(requested);

        RideResponse response = rideService.cancelRide(RIDE_ID, PASSENGER_ID);

        assertEquals(RideStatus.CANCELLED, response.status());
        verify(driverServiceClient, never()).releaseDriver(any());
    }

    @Test
    void releaseFailureDoesNotChangeTerminalRideState() {
        Ride ride = inProgressRide();
        when(rideRepository.findByIdAndDriverId(RIDE_ID, DRIVER_ID)).thenReturn(Optional.of(ride));
        when(rideRepository.save(ride)).thenReturn(ride);
        doThrow(new DriverServiceIntegrationException("release failed"))
                .when(driverServiceClient).releaseDriver(DRIVER_ID);

        RideResponse response = rideService.completeRide(
                RIDE_ID, DRIVER_ID, new BigDecimal("12.345"));

        assertEquals(RideStatus.COMPLETED, response.status());
        assertEquals(NOW, response.completedAt());
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
        Ride ride = assignedRide();
        ride.accept(NOW.minusSeconds(120));
        ride.start(NOW.minusSeconds(60));
        return ride;
    }

    private Ride assignedRide() {
        Ride ride = requestedRide();
        ride.assignDriver(DRIVER_ID, NOW.minusSeconds(180));
        return ride;
    }

    private Ride requestedRide() {
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
        return ride;
    }
}
