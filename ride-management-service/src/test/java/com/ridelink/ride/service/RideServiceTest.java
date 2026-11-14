package com.ridelink.ride.service;

import com.ridelink.ride.client.DriverServiceClient;
import com.ridelink.ride.client.FareServiceClient;
import com.ridelink.ride.client.dto.FareEstimateResponse;
import com.ridelink.ride.domain.Ride;
import com.ridelink.ride.domain.RideLocation;
import com.ridelink.ride.domain.RideStatus;
import com.ridelink.ride.dto.CreateRideRequest;
import com.ridelink.ride.dto.LocationRequest;
import com.ridelink.ride.dto.RideResponse;
import com.ridelink.ride.exception.InvalidRideTransitionException;
import com.ridelink.ride.exception.RideNotFoundException;
import com.ridelink.ride.repository.RideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RideServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-29T04:00:00Z");
    private static final Long GENERATED_RIDE_ID = 101L;
    private static final Long PASSENGER_ID = 42L;
    private static final Long DRIVER_ID = 77L;
    private static final Instant INITIAL_INSTANT = Instant.parse("2026-09-29T03:00:00Z");

    @Mock
    private RideRepository rideRepository;

    @Mock
    private DriverServiceClient driverServiceClient;

    @Mock
    private FareServiceClient fareServiceClient;

    private RideService rideService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        rideService = new RideService(rideRepository, driverServiceClient, fareServiceClient, clock);
        lenient().when(fareServiceClient.estimateFare(any(RideLocation.class), any(RideLocation.class)))
                .thenReturn(new FareEstimateResponse(4.75, 480.0));
    }

    @Test
    void createRideCreatesRequestedRideAndMapsSavedEntity() {
        Long passengerId = 42L;
        LocationRequest pickup = new LocationRequest(
                "Colombo Fort",
                new BigDecimal("6.9344000"),
                new BigDecimal("79.8428000")
        );
        LocationRequest destination = new LocationRequest(
                "Bambalapitiya",
                new BigDecimal("6.8935000"),
                new BigDecimal("79.8552000")
        );
        CreateRideRequest request = new CreateRideRequest("  Colombo  ", pickup, destination);

        when(rideRepository.save(any(Ride.class))).thenAnswer(invocation -> {
            Ride savedRide = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedRide, "id", GENERATED_RIDE_ID);
            return savedRide;
        });

        RideResponse response = rideService.createRide(passengerId, request);

        ArgumentCaptor<Ride> rideCaptor = ArgumentCaptor.forClass(Ride.class);
        verify(rideRepository).save(rideCaptor.capture());
        Ride persistedRide = rideCaptor.getValue();

        assertAll(
                () -> assertEquals(passengerId, persistedRide.getPassengerId()),
                () -> assertEquals("Colombo", persistedRide.getServiceArea()),
                () -> assertEquals(RideStatus.REQUESTED, persistedRide.getStatus()),
                () -> assertNull(persistedRide.getDriverId()),
                () -> assertEquals(FIXED_INSTANT, persistedRide.getRequestedAt()),
                () -> assertEquals(FIXED_INSTANT, persistedRide.getCreatedAt()),
                () -> assertEquals(FIXED_INSTANT, persistedRide.getUpdatedAt()),
                () -> assertNull(persistedRide.getAssignedAt()),
                () -> assertNull(persistedRide.getAcceptedAt()),
                () -> assertNull(persistedRide.getStartedAt()),
                () -> assertNull(persistedRide.getCompletedAt()),
                () -> assertNull(persistedRide.getCancelledAt()),
                () -> assertNull(persistedRide.getActualDistance()),
                () -> assertEquals(pickup.placeName(), persistedRide.getPickup().getPlaceName()),
                () -> assertEquals(pickup.latitude(), persistedRide.getPickup().getLatitude()),
                () -> assertEquals(pickup.longitude(), persistedRide.getPickup().getLongitude()),
                () -> assertEquals(destination.placeName(), persistedRide.getDestination().getPlaceName()),
                () -> assertEquals(destination.latitude(), persistedRide.getDestination().getLatitude()),
                () -> assertEquals(destination.longitude(), persistedRide.getDestination().getLongitude()),
                () -> assertEquals(GENERATED_RIDE_ID, response.id()),
                () -> assertEquals(passengerId, response.passengerId()),
                () -> assertEquals("Colombo", response.serviceArea()),
                () -> assertNull(response.driverId()),
                () -> assertEquals(RideStatus.REQUESTED, response.status()),
                () -> assertEquals(FIXED_INSTANT, response.requestedAt()),
                () -> assertEquals(FIXED_INSTANT, response.createdAt()),
                () -> assertEquals(FIXED_INSTANT, response.updatedAt()),
                () -> assertNull(response.assignedAt()),
                () -> assertNull(response.acceptedAt()),
                () -> assertNull(response.startedAt()),
                () -> assertNull(response.completedAt()),
                () -> assertNull(response.cancelledAt()),
                () -> assertNull(response.actualDistance()),
                () -> assertEquals(pickup.placeName(), response.pickup().placeName()),
                () -> assertEquals(pickup.latitude(), response.pickup().latitude()),
                () -> assertEquals(pickup.longitude(), response.pickup().longitude()),
                () -> assertEquals(destination.placeName(), response.destination().placeName()),
                () -> assertEquals(destination.latitude(), response.destination().latitude()),
                () -> assertEquals(destination.longitude(), response.destination().longitude())
        );
    }

    @Test
    void acceptRideUsesDriverScopedLookupAndSavesRide() {
        Ride ride = assignedRide(GENERATED_RIDE_ID);
        when(rideRepository.findByIdAndDriverId(GENERATED_RIDE_ID, DRIVER_ID))
                .thenReturn(Optional.of(ride));
        when(rideRepository.save(ride)).thenReturn(ride);

        RideResponse response = rideService.acceptRide(GENERATED_RIDE_ID, DRIVER_ID);

        assertAll(
                () -> assertEquals(RideStatus.ACCEPTED, response.status()),
                () -> assertEquals(FIXED_INSTANT, response.acceptedAt()),
                () -> assertEquals(FIXED_INSTANT, response.updatedAt())
        );
        verify(rideRepository).findByIdAndDriverId(GENERATED_RIDE_ID, DRIVER_ID);
        verify(rideRepository).save(ride);
    }

    @Test
    void acceptRideThrowsWhenRideIsMissingOrInaccessible() {
        when(rideRepository.findByIdAndDriverId(GENERATED_RIDE_ID, DRIVER_ID))
                .thenReturn(Optional.empty());

        assertThrows(RideNotFoundException.class,
                () -> rideService.acceptRide(GENERATED_RIDE_ID, DRIVER_ID));

        verify(rideRepository, never()).save(any(Ride.class));
    }

    @Test
    void startRideTransitionsAndSavesDriverScopedRide() {
        Ride ride = acceptedRide(GENERATED_RIDE_ID);
        when(rideRepository.findByIdAndDriverId(GENERATED_RIDE_ID, DRIVER_ID))
                .thenReturn(Optional.of(ride));
        when(rideRepository.save(ride)).thenReturn(ride);

        RideResponse response = rideService.startRide(GENERATED_RIDE_ID, DRIVER_ID);

        assertAll(
                () -> assertEquals(RideStatus.IN_PROGRESS, response.status()),
                () -> assertEquals(FIXED_INSTANT, response.startedAt()),
                () -> assertEquals(FIXED_INSTANT, response.updatedAt())
        );
        verify(rideRepository).save(ride);
    }

    @Test
    void completeRideForwardsActualDistanceAndSavesRide() {
        Ride ride = inProgressRide(GENERATED_RIDE_ID);
        BigDecimal actualDistance = new BigDecimal("12.345");
        when(rideRepository.findByIdAndDriverId(GENERATED_RIDE_ID, DRIVER_ID))
                .thenReturn(Optional.of(ride));
        when(rideRepository.save(ride)).thenReturn(ride);

        RideResponse response = rideService.completeRide(
                GENERATED_RIDE_ID,
                DRIVER_ID,
                actualDistance
        );

        assertAll(
                () -> assertEquals(RideStatus.COMPLETED, response.status()),
                () -> assertEquals(actualDistance, response.actualDistance()),
                () -> assertEquals(FIXED_INSTANT, response.completedAt()),
                () -> assertEquals(FIXED_INSTANT, response.updatedAt())
        );
        verify(rideRepository).save(ride);
    }

    @Test
    void cancelRideUsesPassengerScopedLookupAndSavesRide() {
        Ride ride = requestedRide(GENERATED_RIDE_ID);
        when(rideRepository.findByIdAndPassengerId(GENERATED_RIDE_ID, PASSENGER_ID))
                .thenReturn(Optional.of(ride));
        when(rideRepository.save(ride)).thenReturn(ride);

        RideResponse response = rideService.cancelRide(GENERATED_RIDE_ID, PASSENGER_ID);

        assertAll(
                () -> assertEquals(RideStatus.CANCELLED, response.status()),
                () -> assertEquals(FIXED_INSTANT, response.cancelledAt()),
                () -> assertEquals(FIXED_INSTANT, response.updatedAt())
        );
        verify(rideRepository).findByIdAndPassengerId(GENERATED_RIDE_ID, PASSENGER_ID);
        verify(rideRepository).save(ride);
    }

    @Test
    void getRideReturnsMappedPassengerScopedRide() {
        Ride ride = assignedRide(GENERATED_RIDE_ID);
        when(rideRepository.findByIdAndPassengerId(GENERATED_RIDE_ID, PASSENGER_ID))
                .thenReturn(Optional.of(ride));

        RideResponse response = rideService.getRide(GENERATED_RIDE_ID, PASSENGER_ID);

        assertAll(
                () -> assertEquals(GENERATED_RIDE_ID, response.id()),
                () -> assertEquals(PASSENGER_ID, response.passengerId()),
                () -> assertEquals("Colombo", response.serviceArea()),
                () -> assertEquals(DRIVER_ID, response.driverId()),
                () -> assertEquals(RideStatus.ASSIGNED, response.status())
        );
        verify(rideRepository, never()).save(any(Ride.class));
    }

    @Test
    void getRideThrowsWhenRideIsMissingOrInaccessible() {
        when(rideRepository.findByIdAndPassengerId(GENERATED_RIDE_ID, PASSENGER_ID))
                .thenReturn(Optional.empty());

        assertThrows(RideNotFoundException.class,
                () -> rideService.getRide(GENERATED_RIDE_ID, PASSENGER_ID));

        verify(rideRepository, never()).save(any(Ride.class));
    }

    @Test
    void listPassengerRidesReturnsMappedRidesInRepositoryOrder() {
        Ride newestRide = assignedRide(102L);
        Ride olderRide = requestedRide(101L);
        when(rideRepository.findAllByPassengerIdOrderByRequestedAtDesc(PASSENGER_ID))
                .thenReturn(List.of(newestRide, olderRide));

        List<RideResponse> responses = rideService.listPassengerRides(PASSENGER_ID);

        assertAll(
                () -> assertEquals(2, responses.size()),
                () -> assertEquals(102L, responses.get(0).id()),
                () -> assertEquals(RideStatus.ASSIGNED, responses.get(0).status()),
                () -> assertEquals("Colombo", responses.get(0).serviceArea()),
                () -> assertEquals(101L, responses.get(1).id()),
                () -> assertEquals(RideStatus.REQUESTED, responses.get(1).status()),
                () -> assertEquals("Colombo", responses.get(1).serviceArea())
        );
    }

    @Test
    void listPassengerRidesReturnsEmptyList() {
        when(rideRepository.findAllByPassengerIdOrderByRequestedAtDesc(PASSENGER_ID))
                .thenReturn(List.of());

        List<RideResponse> responses = rideService.listPassengerRides(PASSENGER_ID);

        assertEquals(List.of(), responses);
    }

    @Test
    void getDriverRideReturnsOnlyDriverScopedRide() {
        Ride ride = assignedRide(GENERATED_RIDE_ID);
        when(rideRepository.findByIdAndDriverId(GENERATED_RIDE_ID, DRIVER_ID))
                .thenReturn(Optional.of(ride));

        RideResponse response = rideService.getDriverRide(GENERATED_RIDE_ID, DRIVER_ID);

        assertEquals(GENERATED_RIDE_ID, response.id());
        assertEquals(DRIVER_ID, response.driverId());
        verify(rideRepository).findByIdAndDriverId(GENERATED_RIDE_ID, DRIVER_ID);
    }

    @Test
    void getDriverRideRejectsRideAssignedToAnotherDriver() {
        when(rideRepository.findByIdAndDriverId(GENERATED_RIDE_ID, DRIVER_ID))
                .thenReturn(Optional.empty());

        assertThrows(RideNotFoundException.class,
                () -> rideService.getDriverRide(GENERATED_RIDE_ID, DRIVER_ID));
    }

    @Test
    void listDriverRidesUsesDriverScopedRepositoryQuery() {
        Ride ride = assignedRide(GENERATED_RIDE_ID);
        when(rideRepository.findAllByDriverIdOrderByRequestedAtDesc(DRIVER_ID))
                .thenReturn(List.of(ride));

        List<RideResponse> responses = rideService.listDriverRides(DRIVER_ID);

        assertEquals(List.of(GENERATED_RIDE_ID), responses.stream().map(RideResponse::id).toList());
        verify(rideRepository).findAllByDriverIdOrderByRequestedAtDesc(DRIVER_ID);
    }

    @Test
    void invalidTransitionPropagatesWithoutSaving() {
        Ride ride = requestedRide(GENERATED_RIDE_ID);
        when(rideRepository.findByIdAndDriverId(GENERATED_RIDE_ID, DRIVER_ID))
                .thenReturn(Optional.of(ride));

        assertThrows(InvalidRideTransitionException.class,
                () -> rideService.acceptRide(GENERATED_RIDE_ID, DRIVER_ID));

        verify(rideRepository, never()).save(any(Ride.class));
    }

    private Ride inProgressRide(Long rideId) {
        Ride ride = acceptedRide(rideId);
        ride.start(INITIAL_INSTANT.plusSeconds(180));
        return ride;
    }

    private Ride acceptedRide(Long rideId) {
        Ride ride = assignedRide(rideId);
        ride.accept(INITIAL_INSTANT.plusSeconds(120));
        return ride;
    }

    private Ride assignedRide(Long rideId) {
        Ride ride = requestedRide(rideId);
        ride.assignDriver(DRIVER_ID, INITIAL_INSTANT.plusSeconds(60));
        return ride;
    }

    private Ride requestedRide(Long rideId) {
        Ride ride = Ride.create(
                PASSENGER_ID,
                "Colombo",
                new RideLocation(
                        "Colombo Fort",
                        new BigDecimal("6.9344"),
                        new BigDecimal("79.8428")
                ),
                new RideLocation(
                        "Bambalapitiya",
                        new BigDecimal("6.8935"),
                        new BigDecimal("79.8552")
                ),
                INITIAL_INSTANT,
                INITIAL_INSTANT,
                INITIAL_INSTANT
        );
        ReflectionTestUtils.setField(ride, "id", rideId);
        return ride;
    }}
