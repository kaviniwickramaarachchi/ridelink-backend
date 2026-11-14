package com.ridelink.farepayment.service;

import com.ridelink.farepayment.config.FareProperties;
import org.springframework.stereotype.Service;

@Service
public class DistanceFareCalculator implements FareCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final FareProperties fareProperties;

    public DistanceFareCalculator(FareProperties fareProperties) {
        this.fareProperties = fareProperties;
    }

    @Override
    public double estimateFare(double pickupLat, double pickupLng, double destLat, double destLng) {
        double distanceKm = calculateDistanceKm(pickupLat, pickupLng, destLat, destLng);
        return calculateFare(distanceKm);
    }

    @Override
    public double calculateFinalFare(double distanceKm, double durationMinutes) {
        // durationMinutes is accepted for interface compatibility with Ride Management,
        // but is not used: the agreed formula is base fare + distance-based rate only.
        return calculateFare(distanceKm);
    }

    private double calculateFare(double distanceKm) {
        double fare = fareProperties.getBaseFare() + (fareProperties.getPerKmRate() * distanceKm);
        return roundToTwoDecimals(fare);
    }

    @Override
    public double calculateDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}