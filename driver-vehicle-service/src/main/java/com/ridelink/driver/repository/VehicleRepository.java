package com.ridelink.driver.repository;

import com.ridelink.driver.entity.Vehicle;
import com.ridelink.driver.entity.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByDriver_IdAndVehicleStatus(
            Long driverId,
            VehicleStatus vehicleStatus);

    List<Vehicle> findAllByDriver_IdOrderByCreatedAtDesc(Long driverId);

    boolean existsByRegistrationNumberIgnoreCase(String registrationNumber);

    boolean existsByRegistrationNumberIgnoreCaseAndIdNot(
            String registrationNumber,
            Long vehicleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Vehicle v
            SET v.vehicleStatus = :inactiveStatus
            WHERE v.driver.id = :driverId
              AND v.vehicleStatus = :activeStatus
            """)
    int deactivateActiveVehicles(
            @Param("driverId") Long driverId,
            @Param("activeStatus") VehicleStatus activeStatus,
            @Param("inactiveStatus") VehicleStatus inactiveStatus);
}