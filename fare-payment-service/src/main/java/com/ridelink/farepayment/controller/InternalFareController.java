package com.ridelink.farepayment.controller;

import com.ridelink.farepayment.dto.FareEstimateRequest;
import com.ridelink.farepayment.dto.FareEstimateResponse;
import com.ridelink.farepayment.dto.InternalFinalFareRequest;
import com.ridelink.farepayment.dto.InternalFinalFareResponse;
import com.ridelink.farepayment.entity.FinalFare;
import com.ridelink.farepayment.service.FareCalculator;
import com.ridelink.farepayment.service.FinalFareService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/fares")
public class InternalFareController {

    private final FareCalculator fareCalculator;
    private final FinalFareService finalFareService;

    public InternalFareController(
            FareCalculator fareCalculator,
            FinalFareService finalFareService) {

        this.fareCalculator = fareCalculator;
        this.finalFareService = finalFareService;
    }

    @PostMapping("/estimate")
    public ResponseEntity<FareEstimateResponse> estimate(
            @Valid @RequestBody FareEstimateRequest request) {

        double distanceKm = fareCalculator.calculateDistanceKm(
                request.pickupLatitude(),
                request.pickupLongitude(),
                request.destinationLatitude(),
                request.destinationLongitude());

        double fare = fareCalculator.estimateFare(
                request.pickupLatitude(),
                request.pickupLongitude(),
                request.destinationLatitude(),
                request.destinationLongitude());

        return ResponseEntity.ok(
                new FareEstimateResponse(distanceKm, fare));
    }

    @PostMapping("/final")
    public ResponseEntity<InternalFinalFareResponse> finalFare(
            @Valid @RequestBody InternalFinalFareRequest request) {

        FinalFare finalFare =
                finalFareService.getOrCreateFinalFare(
                        request.rideId(),
                        request.passengerId(),
                        request.distanceKm(),
                        request.durationMinutes());

        InternalFinalFareResponse response =
                new InternalFinalFareResponse(
                        finalFare.getRideId(),
                        finalFare.getDistanceKm(),
                        finalFare.getDurationMinutes(),
                        finalFare.getAmount());

        return ResponseEntity.ok(response);
    }
}