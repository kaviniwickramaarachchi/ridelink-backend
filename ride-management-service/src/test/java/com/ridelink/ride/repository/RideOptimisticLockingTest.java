package com.ridelink.ride.repository;

import com.ridelink.ride.domain.Ride;
import com.ridelink.ride.domain.RideLocation;
import com.ridelink.ride.domain.RideStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:ride-lock-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RideOptimisticLockingTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-09-29T04:00:00Z");
    private static final Instant ASSIGNED_AT = Instant.parse("2026-09-29T04:01:00Z");
    private static final Instant ACCEPTED_AT = Instant.parse("2026-09-29T04:02:00Z");
    private static final Instant CANCELLED_AT = Instant.parse("2026-09-29T04:03:00Z");

    @Autowired
    private RideRepository rideRepository;

    private final TransactionTemplate transactions;

    @Autowired
    RideOptimisticLockingTest(PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Test
    void normalLifecycleUpdateIncrementsVersion() {
        Long rideId = persistAssignedRide();

        Ride accepted = inTransaction(() -> {
            Ride ride = findRide(rideId);
            ride.accept(ACCEPTED_AT);
            return rideRepository.saveAndFlush(ride);
        });

        assertEquals(RideStatus.ACCEPTED, accepted.getStatus());
        assertEquals(1L, accepted.getVersion());
    }

    @Test
    void staleLifecycleUpdateCannotOverwriteNewerState() {
        Long rideId = persistAssignedRide();
        Ride staleRide = inTransaction(() -> findRide(rideId));

        inTransaction(() -> {
            Ride currentRide = findRide(rideId);
            currentRide.accept(ACCEPTED_AT);
            return rideRepository.saveAndFlush(currentRide);
        });

        staleRide.cancel(CANCELLED_AT);

        assertThrows(
                OptimisticLockingFailureException.class,
                () -> inTransaction(() -> rideRepository.saveAndFlush(staleRide))
        );

        Ride persistedRide = inTransaction(() -> findRide(rideId));
        assertEquals(RideStatus.ACCEPTED, persistedRide.getStatus());
        assertEquals(1L, persistedRide.getVersion());
        assertNull(persistedRide.getCancelledAt());
    }

    private Long persistAssignedRide() {
        return inTransaction(() -> {
            Ride ride = Ride.create(
                    42L,
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
                    REQUESTED_AT,
                    REQUESTED_AT,
                    REQUESTED_AT
            );
            ride.assignDriver(77L, ASSIGNED_AT);
            Ride savedRide = rideRepository.saveAndFlush(ride);
            assertNotNull(savedRide.getVersion());
            return savedRide.getId();
        });
    }

    private Ride findRide(Long rideId) {
        return rideRepository.findById(rideId).orElseThrow();
    }

    private <T> T inTransaction(Supplier<T> operation) {
        return transactions.execute(status -> operation.get());
    }
}
