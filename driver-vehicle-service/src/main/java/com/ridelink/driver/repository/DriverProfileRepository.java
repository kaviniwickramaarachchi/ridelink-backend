package com.ridelink.driver.repository;

import com.ridelink.driver.entity.AvailabilityStatus;
import com.ridelink.driver.entity.DriverProfile;
import com.ridelink.driver.entity.VehicleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DriverProfileRepository
        extends JpaRepository<DriverProfile, Long> {

    Optional<DriverProfile> findByAccountId(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DriverProfile d WHERE d.accountId = :accountId")
    Optional<DriverProfile> findByAccountIdForUpdate(
            @Param("accountId") Long accountId);

    boolean existsByAccountId(Long accountId);

    @Query("""
            SELECT d
            FROM DriverProfile d
            WHERE d.availabilityStatus = :availabilityStatus
              AND LOWER(d.serviceArea) = LOWER(:serviceArea)
              AND EXISTS (
                  SELECT v.id
                  FROM Vehicle v
                  WHERE v.driver = d
                    AND v.vehicleStatus = :vehicleStatus
              )
            ORDER BY d.id ASC
            """)
    List<DriverProfile> findEligibleDrivers(
            @Param("serviceArea") String serviceArea,
            @Param("availabilityStatus")
            AvailabilityStatus availabilityStatus,
            @Param("vehicleStatus") VehicleStatus vehicleStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE DriverProfile d
            SET d.availabilityStatus = :newStatus
            WHERE d.id = :driverId
              AND d.availabilityStatus = :expectedStatus
            """)
    int updateAvailabilityIfCurrent(
            @Param("driverId") Long driverId,
            @Param("expectedStatus") AvailabilityStatus expectedStatus,
            @Param("newStatus") AvailabilityStatus newStatus);
}
