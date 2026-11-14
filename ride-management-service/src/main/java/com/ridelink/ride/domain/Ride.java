package com.ridelink.ride.domain;

import com.ridelink.ride.exception.InvalidRideTransitionException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "rides")
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "driver_id")
    private Long driverId;

    @Column(name = "service_area", nullable = false, length = 100)
    private String serviceArea;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "placeName", column = @Column(name = "pickup_place_name", nullable = false, length = 255)),
            @AttributeOverride(name = "latitude", column = @Column(name = "pickup_latitude", nullable = false, precision = 10, scale = 7)),
            @AttributeOverride(name = "longitude", column = @Column(name = "pickup_longitude", nullable = false, precision = 10, scale = 7))
    })
    private RideLocation pickup;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "placeName", column = @Column(name = "destination_place_name", nullable = false, length = 255)),
            @AttributeOverride(name = "latitude", column = @Column(name = "destination_latitude", nullable = false, precision = 10, scale = 7)),
            @AttributeOverride(name = "longitude", column = @Column(name = "destination_longitude", nullable = false, precision = 10, scale = 7))
    })
    private RideLocation destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RideStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "actual_distance", precision = 10, scale = 3)
    private BigDecimal actualDistance;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Ride() {
    }

    public static Ride create(
            Long passengerId,
            String serviceArea,
            RideLocation pickup,
            RideLocation destination,
            Instant requestedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        if (serviceArea == null || serviceArea.isBlank()) {
            throw new IllegalArgumentException("serviceArea must not be blank");
        }

        String normalizedServiceArea = serviceArea.trim();
        if (normalizedServiceArea.length() > 100) {
            throw new IllegalArgumentException("serviceArea must not exceed 100 characters");
        }

        Ride ride = new Ride();
        ride.passengerId = passengerId;
        ride.driverId = null;
        ride.serviceArea = normalizedServiceArea;
        ride.pickup = pickup;
        ride.destination = destination;
        ride.status = RideStatus.REQUESTED;
        ride.requestedAt = requestedAt;
        ride.assignedAt = null;
        ride.acceptedAt = null;
        ride.startedAt = null;
        ride.completedAt = null;
        ride.cancelledAt = null;
        ride.actualDistance = null;
        ride.createdAt = createdAt;
        ride.updatedAt = updatedAt;
        return ride;
    }

    public void assignDriver(Long driverId, Instant occurredAt) {
        requireState(RideStatus.REQUESTED, RideStatus.ASSIGNED);
        if (driverId == null || driverId <= 0) {
            throw new IllegalArgumentException("driverId must be positive");
        }
        requireOccurredAt(occurredAt);

        this.driverId = driverId;
        this.status = RideStatus.ASSIGNED;
        this.assignedAt = occurredAt;
        this.updatedAt = occurredAt;
    }

    public void accept(Instant occurredAt) {
        requireState(RideStatus.ASSIGNED, RideStatus.ACCEPTED);
        requireOccurredAt(occurredAt);

        this.status = RideStatus.ACCEPTED;
        this.acceptedAt = occurredAt;
        this.updatedAt = occurredAt;
    }

    public void start(Instant occurredAt) {
        requireState(RideStatus.ACCEPTED, RideStatus.IN_PROGRESS);
        requireOccurredAt(occurredAt);

        this.status = RideStatus.IN_PROGRESS;
        this.startedAt = occurredAt;
        this.updatedAt = occurredAt;
    }

    public void complete(BigDecimal actualDistance, Instant occurredAt) {
        requireState(RideStatus.IN_PROGRESS, RideStatus.COMPLETED);
        if (actualDistance == null || actualDistance.signum() <= 0) {
            throw new IllegalArgumentException("actualDistance must be greater than zero");
        }
        requireOccurredAt(occurredAt);

        this.status = RideStatus.COMPLETED;
        this.actualDistance = actualDistance;
        this.completedAt = occurredAt;
        this.updatedAt = occurredAt;
    }

    public void cancel(Instant occurredAt) {
        if (status != RideStatus.REQUESTED
                && status != RideStatus.ASSIGNED
                && status != RideStatus.ACCEPTED) {
            throw new InvalidRideTransitionException(status, RideStatus.CANCELLED);
        }
        requireOccurredAt(occurredAt);

        this.status = RideStatus.CANCELLED;
        this.cancelledAt = occurredAt;
        this.updatedAt = occurredAt;
    }

    private void requireState(RideStatus requiredStatus, RideStatus targetStatus) {
        if (status != requiredStatus) {
            throw new InvalidRideTransitionException(status, targetStatus);
        }
    }

    private void requireOccurredAt(Instant occurredAt) {
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
    }

    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public Long getPassengerId() { return passengerId; }
    public Long getDriverId() { return driverId; }
    public String getServiceArea() { return serviceArea; }
    public RideLocation getPickup() { return pickup; }
    public RideLocation getDestination() { return destination; }
    public RideStatus getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public BigDecimal getActualDistance() { return actualDistance; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
