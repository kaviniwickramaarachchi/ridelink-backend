package com.ridelink.farepayment.service;

import com.ridelink.farepayment.entity.FinalFare;
import com.ridelink.farepayment.exception.FinalFareConflictException;
import com.ridelink.farepayment.exception.FinalFareNotFoundException;
import com.ridelink.farepayment.repository.FinalFareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinalFareService {

    private static final double MATCH_TOLERANCE = 0.01;

    private final FinalFareRepository finalFareRepository;
    private final FareCalculator fareCalculator;

    public FinalFareService(
            FinalFareRepository finalFareRepository,
            FareCalculator fareCalculator) {

        this.finalFareRepository = finalFareRepository;
        this.fareCalculator = fareCalculator;
    }

    @Transactional
    public FinalFare getOrCreateFinalFare(
            Long rideId,
            Long passengerId,
            double distanceKm,
            double durationMinutes) {

        return finalFareRepository.findByRideId(rideId)
                .map(existing -> validateMatchesOrConflict(
                        existing,
                        passengerId,
                        distanceKm,
                        durationMinutes))
                .orElseGet(() -> createFinalFare(
                        rideId,
                        passengerId,
                        distanceKm,
                        durationMinutes));
    }

    private FinalFare validateMatchesOrConflict(
            FinalFare existing,
            Long passengerId,
            double distanceKm,
            double durationMinutes) {

        boolean passengerMatches =
                existing.getPassengerId() != null
                        && existing.getPassengerId().equals(passengerId);

        boolean distanceMatches =
                Math.abs(existing.getDistanceKm() - distanceKm)
                        < MATCH_TOLERANCE;

        boolean durationMatches =
                Math.abs(existing.getDurationMinutes() - durationMinutes)
                        < MATCH_TOLERANCE;

        if (passengerMatches && distanceMatches && durationMatches) {
            return existing;
        }

        throw new FinalFareConflictException(
                "A final fare already exists for ride id "
                        + existing.getRideId()
                        + " with different passenger, distance, or duration values");
    }

    private FinalFare createFinalFare(
            Long rideId,
            Long passengerId,
            double distanceKm,
            double durationMinutes) {

        double amount =
                fareCalculator.calculateFinalFare(distanceKm, durationMinutes);

        FinalFare finalFare = new FinalFare();

        finalFare.setRideId(rideId);
        finalFare.setPassengerId(passengerId);
        finalFare.setDistanceKm(distanceKm);
        finalFare.setDurationMinutes(durationMinutes);
        finalFare.setAmount(amount);

        return finalFareRepository.save(finalFare);
    }

    public FinalFare getFinalFareByRideId(Long rideId) {

        return finalFareRepository.findByRideId(rideId)
                .orElseThrow(() -> new FinalFareNotFoundException(
                        "No final fare has been calculated yet for ride id "
                                + rideId));
    }
}