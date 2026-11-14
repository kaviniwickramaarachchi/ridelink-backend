package com.ridelink.ride.client;

import com.ridelink.ride.client.dto.FareEstimateResponse;
import com.ridelink.ride.client.dto.InternalFinalFareResponse;
import com.ridelink.ride.domain.RideLocation;

import java.math.BigDecimal;

public interface FareServiceClient {

    FareEstimateResponse estimateFare(RideLocation pickup, RideLocation destination);

    InternalFinalFareResponse createFinalFare(
            Long rideId,
            Long passengerId,
            BigDecimal distanceKm,
            double durationMinutes
    );
}
