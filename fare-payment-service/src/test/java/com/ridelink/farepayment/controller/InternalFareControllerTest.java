package com.ridelink.farepayment.controller;

import com.ridelink.farepayment.dto.FareEstimateRequest;
import com.ridelink.farepayment.dto.InternalFinalFareRequest;
import com.ridelink.farepayment.entity.FinalFare;
import com.ridelink.farepayment.service.FareCalculator;
import com.ridelink.farepayment.service.FinalFareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalFareControllerTest {

    @Mock
    private FareCalculator fareCalculator;

    @Mock
    private FinalFareService finalFareService;

    private InternalFareController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalFareController(fareCalculator, finalFareService);
    }

    @Test
    void estimate_returnsDistanceAndFareFromCalculator() {
        when(fareCalculator.calculateDistanceKm(6.9, 79.8, 6.8, 79.9)).thenReturn(10.0);
        when(fareCalculator.estimateFare(6.9, 79.8, 6.8, 79.9)).thenReturn(900.0);

        FareEstimateRequest request = new FareEstimateRequest(6.9, 79.8, 6.8, 79.9);

        var response = controller.estimate(request);

        assertEquals(10.0, response.getBody().distanceKm());
        assertEquals(900.0, response.getBody().estimatedFare());
    }

    @Test
    void finalFare_delegatesToFinalFareServiceAndReturnsStoredResult() {
        FinalFare finalFare = new FinalFare();
        finalFare.setRideId(5L);
        finalFare.setDistanceKm(10.0);
        finalFare.setDurationMinutes(15.0);
        finalFare.setAmount(900.0);

       when(finalFareService.getOrCreateFinalFare(
        5L, 42L, 10.0, 15.0))
        .thenReturn(finalFare);
        
        InternalFinalFareRequest request =
        new InternalFinalFareRequest(5L, 42L, 10.0, 15.0);

        var response = controller.finalFare(request);

        assertEquals(5L, response.getBody().rideId());
        assertEquals(900.0, response.getBody().amount());
    }
}