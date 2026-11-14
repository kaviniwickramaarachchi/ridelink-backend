package com.ridelink.ride.service;

import com.ridelink.ride.client.DriverReservationConflictException;
import com.ridelink.ride.client.DriverServiceClient;
import com.ridelink.ride.client.DriverServiceIntegrationException;
import com.ridelink.ride.client.FareServiceClient;
import com.ridelink.ride.client.FareServiceIntegrationException;
import com.ridelink.ride.client.dto.EligibleDriverResponse;
import com.ridelink.ride.domain.Ride;
import com.ridelink.ride.domain.RideLocation;
import com.ridelink.ride.domain.RideStatus;
import com.ridelink.ride.dto.CreateRideRequest;
import com.ridelink.ride.dto.LocationRequest;
import com.ridelink.ride.dto.LocationResponse;
import com.ridelink.ride.dto.RideResponse;
import com.ridelink.ride.exception.RideNotFoundException;
import com.ridelink.ride.repository.RideRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class RideService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RideService.class);

    private final RideRepository rideRepository;
    private final DriverServiceClient driverServiceClient;
    private final FareServiceClient fareServiceClient;
    private final Clock clock;

    public RideService(
            RideRepository rideRepository,
            DriverServiceClient driverServiceClient,
            FareServiceClient fareServiceClient,
            Clock clock
    ) {
        this.rideRepository = rideRepository;
        this.driverServiceClient = driverServiceClient;
        this.fareServiceClient = fareServiceClient;
        this.clock = clock;
    }

    @Transactional
    public RideResponse createRide(Long passengerId, CreateRideRequest request) {
        RideLocation pickup = toRideLocation(request.pickup());
        RideLocation destination = toRideLocation(request.destination());
        Instant now = Instant.now(clock);

        fareServiceClient.estimateFare(pickup, destination);

        Ride ride = Ride.create(passengerId, request.serviceArea(), pickup, destination, now, now, now);
        Ride savedRide = rideRepository.save(ride);
        assignEligibleDriver(savedRide);

        return toRideResponse(savedRide);
    }

    @Transactional
    public RideResponse acceptRide(Long rideId, Long driverId) {
        Ride ride = findDriverRide(rideId, driverId);
        Instant now = Instant.now(clock);

        ride.accept(now);
        return toRideResponse(rideRepository.save(ride));
    }

    @Transactional
    public RideResponse startRide(Long rideId, Long driverId) {
        Ride ride = findDriverRide(rideId, driverId);
        Instant now = Instant.now(clock);

        ride.start(now);
        return toRideResponse(rideRepository.save(ride));
    }

    @Transactional
    public RideResponse completeRide(Long rideId, Long driverId, BigDecimal actualDistance) {
        Ride ride = findDriverRide(rideId, driverId);
        Instant now = Instant.now(clock);
        double durationMinutes = durationMinutes(ride.getStartedAt(), now);

        ride.complete(actualDistance, now);
        Ride savedRide = rideRepository.save(ride);
        createFinalFareAfterCommit(savedRide, actualDistance, durationMinutes);
        releaseDriverAfterCommit(savedRide.getDriverId());
        return toRideResponse(savedRide);
    }

    @Transactional
    public RideResponse cancelRide(Long rideId, Long passengerId) {
        Ride ride = findPassengerRide(rideId, passengerId);
        RideStatus previousStatus = ride.getStatus();
        Long assignedDriverId = ride.getDriverId();
        Instant now = Instant.now(clock);

        ride.cancel(now);
        Ride savedRide = rideRepository.save(ride);
        if ((previousStatus == RideStatus.ASSIGNED || previousStatus == RideStatus.ACCEPTED)
                && assignedDriverId != null) {
            releaseDriverAfterCommit(assignedDriverId);
        }
        return toRideResponse(savedRide);
    }

    @Transactional(readOnly = true)
    public RideResponse getRide(Long rideId, Long passengerId) {
        return toRideResponse(findPassengerRide(rideId, passengerId));
    }

    @Transactional(readOnly = true)
    public List<RideResponse> listPassengerRides(Long passengerId) {
        return rideRepository.findAllByPassengerIdOrderByRequestedAtDesc(passengerId)
                .stream()
                .map(this::toRideResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RideResponse getDriverRide(Long rideId, Long driverId) {
        return toRideResponse(findDriverRide(rideId, driverId));
    }

    @Transactional(readOnly = true)
    public List<RideResponse> listDriverRides(Long driverId) {
        return rideRepository.findAllByDriverIdOrderByRequestedAtDesc(driverId)
                .stream()
                .map(this::toRideResponse)
                .toList();
    }

    private Ride findPassengerRide(Long rideId, Long passengerId) {
        return rideRepository.findByIdAndPassengerId(rideId, passengerId)
                .orElseThrow(() -> new RideNotFoundException(rideId));
    }

    private Ride findDriverRide(Long rideId, Long driverId) {
        return rideRepository.findByIdAndDriverId(rideId, driverId)
                .orElseThrow(() -> new RideNotFoundException(rideId));
    }

    private void assignEligibleDriver(Ride ride) {
        List<EligibleDriverResponse> candidates;
        try {
            candidates = driverServiceClient.findEligibleDrivers(ride.getServiceArea());
        } catch (DriverServiceIntegrationException exception) {
            LOGGER.warn("Driver eligibility lookup failed for ride {}; leaving it REQUESTED", ride.getId(), exception);
            return;
        }

        for (EligibleDriverResponse candidate : candidates) {
            Long driverId = candidate.driverId();
            try {
                driverServiceClient.reserveDriver(driverId);
            } catch (DriverReservationConflictException exception) {
                continue;
            } catch (DriverServiceIntegrationException exception) {
                LOGGER.warn("Driver reservation failed for ride {}; leaving it REQUESTED", ride.getId(), exception);
                return;
            }

            boolean rollbackReleaseRegistered = registerDriverReleaseOnRollback(driverId);
            try {
                ride.assignDriver(driverId, Instant.now(clock));
                rideRepository.save(ride);
            } catch (RuntimeException exception) {
                if (!rollbackReleaseRegistered) {
                    releaseDriverBestEffort(driverId);
                }
                throw exception;
            }
            return;
        }
    }

    private boolean registerDriverReleaseOnRollback(Long driverId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    releaseDriverBestEffort(driverId);
                }
            }
        });
        return true;
    }

    private void releaseDriverAfterCommit(Long driverId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    releaseDriverBestEffort(driverId);
                }
            });
            return;
        }
        releaseDriverBestEffort(driverId);
    }

    private void releaseDriverBestEffort(Long driverId) {
        try {
            driverServiceClient.releaseDriver(driverId);
        } catch (DriverServiceIntegrationException exception) {
            LOGGER.error("Failed to release Driver Service driver {}", driverId, exception);
        }
    }

    private void createFinalFareAfterCommit(Ride ride, BigDecimal actualDistance, double durationMinutes) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    createFinalFareBestEffort(ride.getId(), ride.getPassengerId(), actualDistance, durationMinutes);
                }
            });
            return;
        }
        createFinalFareBestEffort(ride.getId(), ride.getPassengerId(), actualDistance, durationMinutes);
    }

    private void createFinalFareBestEffort(
            Long rideId,
            Long passengerId,
            BigDecimal actualDistance,
            double durationMinutes
    ) {
        try {
            fareServiceClient.createFinalFare(rideId, passengerId, actualDistance, durationMinutes);
        } catch (FareServiceIntegrationException exception) {
            LOGGER.error("Failed to create Fare Service final fare for ride {}", rideId, exception);
        }
    }

    private double durationMinutes(Instant startedAt, Instant completedAt) {
        return Duration.between(startedAt, completedAt).toSeconds() / 60.0;
    }

    private RideLocation toRideLocation(LocationRequest location) {
        return new RideLocation(location.placeName(), location.latitude(), location.longitude());
    }

    private RideResponse toRideResponse(Ride ride) {
        return new RideResponse(
                ride.getId(),
                ride.getPassengerId(),
                ride.getDriverId(),
                ride.getServiceArea(),
                toLocationResponse(ride.getPickup()),
                toLocationResponse(ride.getDestination()),
                ride.getStatus(),
                ride.getRequestedAt(),
                ride.getAssignedAt(),
                ride.getAcceptedAt(),
                ride.getStartedAt(),
                ride.getCompletedAt(),
                ride.getCancelledAt(),
                ride.getActualDistance(),
                ride.getCreatedAt(),
                ride.getUpdatedAt()
        );
    }

    private LocationResponse toLocationResponse(RideLocation location) {
        return new LocationResponse(
                location.getPlaceName(),
                location.getLatitude(),
                location.getLongitude()
        );
    }
}
