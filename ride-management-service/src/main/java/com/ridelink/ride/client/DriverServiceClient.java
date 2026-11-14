package com.ridelink.ride.client;

import com.ridelink.ride.client.dto.EligibleDriverResponse;

import java.util.List;

public interface DriverServiceClient {

    Long resolveDriverId(Long accountId);

    List<EligibleDriverResponse> findEligibleDrivers(String serviceArea);

    void reserveDriver(Long driverId);

    void releaseDriver(Long driverId);
}
