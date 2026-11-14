package com.ridelink.ride.repository;

import com.ridelink.ride.domain.Ride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RideRepository extends JpaRepository<Ride, Long> {

    Optional<Ride> findByIdAndPassengerId(Long id, Long passengerId);

    Optional<Ride> findByIdAndDriverId(Long id, Long driverId);

    List<Ride> findAllByPassengerIdOrderByRequestedAtDesc(Long passengerId);

    List<Ride> findAllByDriverIdOrderByRequestedAtDesc(Long driverId);
}
