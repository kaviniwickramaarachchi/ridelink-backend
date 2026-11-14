package com.ridelink.ride.controller;

import com.ridelink.ride.config.OpenApiConfiguration;
import com.ridelink.ride.dto.CompleteRideRequest;
import com.ridelink.ride.dto.CreateRideRequest;
import com.ridelink.ride.dto.RideResponse;
import com.ridelink.ride.security.CurrentDriverProvider;
import com.ridelink.ride.security.CurrentPassengerProvider;
import com.ridelink.ride.service.RideService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rides")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class RideController {

    private final RideService rideService;
    private final CurrentPassengerProvider currentPassengerProvider;
    private final CurrentDriverProvider currentDriverProvider;

    public RideController(
            RideService rideService,
            CurrentPassengerProvider currentPassengerProvider,
            CurrentDriverProvider currentDriverProvider
    ) {
        this.rideService = rideService;
        this.currentPassengerProvider = currentPassengerProvider;
        this.currentDriverProvider = currentDriverProvider;
    }

    @PostMapping
    public ResponseEntity<RideResponse> createRide(@Valid @RequestBody CreateRideRequest request) {
        Long passengerId = currentPassengerProvider.getCurrentPassengerId();
        RideResponse response = rideService.createRide(passengerId, request);
        URI location = URI.create("/api/v1/rides/" + response.id());

        return ResponseEntity.created(location).body(response);
    }

    @PatchMapping("/{rideId}/accept")
    public RideResponse acceptRide(@PathVariable Long rideId) {
        Long driverId = currentDriverProvider.getCurrentDriverId();
        return rideService.acceptRide(rideId, driverId);
    }

    @PatchMapping("/{rideId}/start")
    public RideResponse startRide(@PathVariable Long rideId) {
        Long driverId = currentDriverProvider.getCurrentDriverId();
        return rideService.startRide(rideId, driverId);
    }

    @PatchMapping("/{rideId}/complete")
    public RideResponse completeRide(
            @PathVariable Long rideId,
            @Valid @RequestBody CompleteRideRequest request
    ) {
        Long driverId = currentDriverProvider.getCurrentDriverId();
        return rideService.completeRide(rideId, driverId, request.actualDistance());
    }

    @PatchMapping("/{rideId}/cancel")
    public RideResponse cancelRide(@PathVariable Long rideId) {
        Long passengerId = currentPassengerProvider.getCurrentPassengerId();
        return rideService.cancelRide(rideId, passengerId);
    }

    @GetMapping("/{rideId}")
    public RideResponse getRide(@PathVariable Long rideId) {
        Long passengerId = currentPassengerProvider.getCurrentPassengerId();
        return rideService.getRide(rideId, passengerId);
    }

    @GetMapping
    public List<RideResponse> listPassengerRides() {
        Long passengerId = currentPassengerProvider.getCurrentPassengerId();
        return rideService.listPassengerRides(passengerId);
    }

    @GetMapping("/driver")
    public List<RideResponse> listDriverRides() {
        Long driverId = currentDriverProvider.getCurrentDriverId();
        return rideService.listDriverRides(driverId);
    }

    @GetMapping("/driver/{rideId}")
    public RideResponse getDriverRide(@PathVariable Long rideId) {
        Long driverId = currentDriverProvider.getCurrentDriverId();
        return rideService.getDriverRide(rideId, driverId);
    }
}
