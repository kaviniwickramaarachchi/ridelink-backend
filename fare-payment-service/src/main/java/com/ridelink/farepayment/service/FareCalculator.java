package com.ridelink.farepayment.service;

public interface FareCalculator {

    double estimateFare(double pickupLat, double pickupLng, double destLat, double destLng);

    double calculateFinalFare(double distanceKm, double durationMinutes);

    double calculateDistanceKm(double lat1, double lng1, double lat2, double lng2);
}