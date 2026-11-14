package com.ridelink.ride.domain;

import com.ridelink.ride.exception.InvalidRideTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RideTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-09-29T04:00:00Z");
    private static final Instant ASSIGNED_AT = Instant.parse("2026-09-29T04:01:00Z");
    private static final Instant ACCEPTED_AT = Instant.parse("2026-09-29T04:02:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-09-29T04:03:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-09-29T04:15:00Z");
    private static final Instant CANCELLED_AT = Instant.parse("2026-09-29T04:04:00Z");
    private static final Long DRIVER_ID = 77L;
    private static final BigDecimal ACTUAL_DISTANCE = new BigDecimal("12.345");

    @Test
    void createTrimsAndStoresServiceArea() {
        Ride ride = Ride.create(
                42L,
                "  Colombo  ",
                new RideLocation("Colombo Fort", new BigDecimal("6.9344"), new BigDecimal("79.8428")),
                new RideLocation("Bambalapitiya", new BigDecimal("6.8935"), new BigDecimal("79.8552")),
                REQUESTED_AT,
                REQUESTED_AT,
                REQUESTED_AT
        );

        assertEquals("Colombo", ride.getServiceArea());
    }

    @ParameterizedTest
    @MethodSource("invalidServiceAreas")
    void createRejectsInvalidServiceArea(String serviceArea) {
        assertThrows(
                IllegalArgumentException.class,
                () -> Ride.create(
                        42L,
                        serviceArea,
                        new RideLocation("Colombo Fort", new BigDecimal("6.9344"), new BigDecimal("79.8428")),
                        new RideLocation("Bambalapitiya", new BigDecimal("6.8935"), new BigDecimal("79.8552")),
                        REQUESTED_AT,
                        REQUESTED_AT,
                        REQUESTED_AT
                )
        );
    }
    @Test
    void assignDriverTransitionsRequestedRideToAssigned() {
        Ride ride = requestedRide();

        ride.assignDriver(DRIVER_ID, ASSIGNED_AT);

        assertAll(
                () -> assertEquals(RideStatus.ASSIGNED, ride.getStatus()),
                () -> assertEquals(DRIVER_ID, ride.getDriverId()),
                () -> assertEquals(ASSIGNED_AT, ride.getAssignedAt()),
                () -> assertEquals(ASSIGNED_AT, ride.getUpdatedAt())
        );
    }

    @Test
    void acceptTransitionsAssignedRideToAccepted() {
        Ride ride = rideInState(RideStatus.ASSIGNED);

        ride.accept(ACCEPTED_AT);

        assertAll(
                () -> assertEquals(RideStatus.ACCEPTED, ride.getStatus()),
                () -> assertEquals(ACCEPTED_AT, ride.getAcceptedAt()),
                () -> assertEquals(ACCEPTED_AT, ride.getUpdatedAt())
        );
    }

    @Test
    void startTransitionsAcceptedRideToInProgress() {
        Ride ride = rideInState(RideStatus.ACCEPTED);

        ride.start(STARTED_AT);

        assertAll(
                () -> assertEquals(RideStatus.IN_PROGRESS, ride.getStatus()),
                () -> assertEquals(STARTED_AT, ride.getStartedAt()),
                () -> assertEquals(STARTED_AT, ride.getUpdatedAt())
        );
    }

    @Test
    void completeTransitionsInProgressRideAndStoresDistance() {
        Ride ride = rideInState(RideStatus.IN_PROGRESS);

        ride.complete(ACTUAL_DISTANCE, COMPLETED_AT);

        assertAll(
                () -> assertEquals(RideStatus.COMPLETED, ride.getStatus()),
                () -> assertEquals(ACTUAL_DISTANCE, ride.getActualDistance()),
                () -> assertEquals(COMPLETED_AT, ride.getCompletedAt()),
                () -> assertEquals(COMPLETED_AT, ride.getUpdatedAt())
        );
    }

    @Test
    void cancelTransitionsRequestedRideToCancelled() {
        assertCancellationFrom(RideStatus.REQUESTED);
    }

    @Test
    void cancelTransitionsAssignedRideToCancelled() {
        assertCancellationFrom(RideStatus.ASSIGNED);
    }

    @Test
    void cancelTransitionsAcceptedRideToCancelled() {
        assertCancellationFrom(RideStatus.ACCEPTED);
    }

    @Test
    void assignDriverRejectsNonRequestedRide() {
        Ride ride = rideInState(RideStatus.ASSIGNED);

        assertThrows(
                InvalidRideTransitionException.class,
                () -> ride.assignDriver(88L, ACCEPTED_AT)
        );
        assertEquals(RideStatus.ASSIGNED, ride.getStatus());
    }

    @Test
    void acceptRejectsNonAssignedRide() {
        Ride ride = requestedRide();

        assertThrows(InvalidRideTransitionException.class, () -> ride.accept(ACCEPTED_AT));
        assertEquals(RideStatus.REQUESTED, ride.getStatus());
    }

    @Test
    void startRejectsNonAcceptedRide() {
        Ride ride = rideInState(RideStatus.ASSIGNED);

        assertThrows(InvalidRideTransitionException.class, () -> ride.start(STARTED_AT));
        assertEquals(RideStatus.ASSIGNED, ride.getStatus());
    }

    @Test
    void completeRejectsNonInProgressRide() {
        Ride ride = rideInState(RideStatus.ACCEPTED);

        assertThrows(
                InvalidRideTransitionException.class,
                () -> ride.complete(ACTUAL_DISTANCE, COMPLETED_AT)
        );
        assertEquals(RideStatus.ACCEPTED, ride.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {"IN_PROGRESS", "COMPLETED", "CANCELLED"})
    void cancelRejectsForbiddenStates(RideStatus status) {
        Ride ride = rideInState(status);
        Instant previousUpdatedAt = ride.getUpdatedAt();
        Instant previousCancelledAt = ride.getCancelledAt();

        assertThrows(InvalidRideTransitionException.class, () -> ride.cancel(CANCELLED_AT));

        assertAll(
                () -> assertEquals(status, ride.getStatus()),
                () -> assertEquals(previousCancelledAt, ride.getCancelledAt()),
                () -> assertEquals(previousUpdatedAt, ride.getUpdatedAt())
        );
    }

    @ParameterizedTest
    @MethodSource("invalidDriverIds")
    void assignDriverRejectsInvalidDriverId(Long driverId) {
        Ride ride = requestedRide();

        assertThrows(IllegalArgumentException.class, () -> ride.assignDriver(driverId, ASSIGNED_AT));

        assertAll(
                () -> assertEquals(RideStatus.REQUESTED, ride.getStatus()),
                () -> assertNull(ride.getDriverId()),
                () -> assertNull(ride.getAssignedAt()),
                () -> assertEquals(REQUESTED_AT, ride.getUpdatedAt())
        );
    }

    @ParameterizedTest
    @MethodSource("invalidActualDistances")
    void completeRejectsInvalidActualDistance(BigDecimal actualDistance) {
        Ride ride = rideInState(RideStatus.IN_PROGRESS);
        Instant previousUpdatedAt = ride.getUpdatedAt();

        assertThrows(
                IllegalArgumentException.class,
                () -> ride.complete(actualDistance, COMPLETED_AT)
        );

        assertAll(
                () -> assertEquals(RideStatus.IN_PROGRESS, ride.getStatus()),
                () -> assertNull(ride.getActualDistance()),
                () -> assertNull(ride.getCompletedAt()),
                () -> assertEquals(previousUpdatedAt, ride.getUpdatedAt())
        );
    }

    @ParameterizedTest
    @EnumSource(TransitionOperation.class)
    void lifecycleOperationsRejectNullOccurredAtWithoutMutation(TransitionOperation operation) {
        Ride ride = rideReadyFor(operation);
        RideStatus previousStatus = ride.getStatus();
        Instant previousUpdatedAt = ride.getUpdatedAt();

        assertThrows(IllegalArgumentException.class, () -> invokeWithNullOccurredAt(ride, operation));

        assertAll(
                () -> assertEquals(previousStatus, ride.getStatus()),
                () -> assertEquals(previousUpdatedAt, ride.getUpdatedAt())
        );
    }

    @Test
    void failedTransitionDoesNotPartiallyMutateRide() {
        Ride ride = rideInState(RideStatus.ACCEPTED);

        assertThrows(
                InvalidRideTransitionException.class,
                () -> ride.complete(ACTUAL_DISTANCE, COMPLETED_AT)
        );

        assertAll(
                () -> assertEquals(RideStatus.ACCEPTED, ride.getStatus()),
                () -> assertNull(ride.getActualDistance()),
                () -> assertNull(ride.getCompletedAt()),
                () -> assertEquals(ACCEPTED_AT, ride.getUpdatedAt())
        );
    }

    private void assertCancellationFrom(RideStatus initialStatus) {
        Ride ride = rideInState(initialStatus);

        ride.cancel(CANCELLED_AT);

        assertAll(
                () -> assertEquals(RideStatus.CANCELLED, ride.getStatus()),
                () -> assertEquals(CANCELLED_AT, ride.getCancelledAt()),
                () -> assertEquals(CANCELLED_AT, ride.getUpdatedAt())
        );
    }

    private Ride rideReadyFor(TransitionOperation operation) {
        return switch (operation) {
            case ASSIGN -> rideInState(RideStatus.REQUESTED);
            case ACCEPT -> rideInState(RideStatus.ASSIGNED);
            case START -> rideInState(RideStatus.ACCEPTED);
            case COMPLETE -> rideInState(RideStatus.IN_PROGRESS);
            case CANCEL -> rideInState(RideStatus.REQUESTED);
        };
    }

    private void invokeWithNullOccurredAt(Ride ride, TransitionOperation operation) {
        switch (operation) {
            case ASSIGN -> ride.assignDriver(DRIVER_ID, null);
            case ACCEPT -> ride.accept(null);
            case START -> ride.start(null);
            case COMPLETE -> ride.complete(ACTUAL_DISTANCE, null);
            case CANCEL -> ride.cancel(null);
        }
    }

    private Ride rideInState(RideStatus status) {
        Ride ride = requestedRide();
        if (status == RideStatus.REQUESTED) {
            return ride;
        }

        ride.assignDriver(DRIVER_ID, ASSIGNED_AT);
        if (status == RideStatus.ASSIGNED) {
            return ride;
        }

        ride.accept(ACCEPTED_AT);
        if (status == RideStatus.ACCEPTED) {
            return ride;
        }

        ride.start(STARTED_AT);
        if (status == RideStatus.IN_PROGRESS) {
            return ride;
        }

        if (status == RideStatus.COMPLETED) {
            ride.complete(ACTUAL_DISTANCE, COMPLETED_AT);
            return ride;
        }

        ride = requestedRide();
        ride.cancel(CANCELLED_AT);
        return ride;
    }

    private Ride requestedRide() {
        return Ride.create(
                42L,
                "Colombo",
                new RideLocation("Colombo Fort", new BigDecimal("6.9344"), new BigDecimal("79.8428")),
                new RideLocation("Bambalapitiya", new BigDecimal("6.8935"), new BigDecimal("79.8552")),
                REQUESTED_AT,
                REQUESTED_AT,
                REQUESTED_AT
        );
    }

    private static Stream<String> invalidServiceAreas() {
        return Stream.of(null, " ", "a".repeat(101));
    }
    private static Stream<Long> invalidDriverIds() {
        return Stream.of(null, 0L, -1L);
    }

    private static Stream<BigDecimal> invalidActualDistances() {
        return Stream.of(null, BigDecimal.ZERO, new BigDecimal("-0.001"));
    }

    private enum TransitionOperation {
        ASSIGN,
        ACCEPT,
        START,
        COMPLETE,
        CANCEL
    }
}
