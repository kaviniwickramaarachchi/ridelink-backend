package com.ridelink.farepayment.service;

import com.ridelink.farepayment.config.FareProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistanceFareCalculatorTest {

    private DistanceFareCalculator calculator;

    @BeforeEach
    void setUp() {
        FareProperties props = new FareProperties();
        props.setBaseFare(100);
        props.setPerKmRate(80);
        calculator = new DistanceFareCalculator(props);
    }

    @Test
    void estimateFare_forNormalDistance_appliesBaseAndPerKmRate() {
        double fare = calculator.estimateFare(6.9344, 79.8428, 6.8500, 79.8650);
        assertTrue(fare > 100, "Fare should exceed the base fare for a real distance");
    }

    @Test
    void estimateFare_forZeroDistance_returnsBaseFareOnly() {
        double fare = calculator.estimateFare(6.9344, 79.8428, 6.9344, 79.8428);
        assertEquals(100.0, fare, 0.01);
    }

    @Test
    void calculateFinalFare_matchesBasePlusDistance() {
        double fare = calculator.calculateFinalFare(10.0, 20.0);
        // 100 + (80 * 10) = 900; duration is ignored per the agreed formula
        assertEquals(900.0, fare, 0.01);
    }

    @Test
    void calculateFinalFare_zeroDistance_returnsBaseFareOnly() {
        double fare = calculator.calculateFinalFare(0.0, 15.0);
        assertEquals(100.0, fare, 0.01);
    }

    @Test
    void calculateDistanceKm_sameCoordinates_returnsZero() {
        double distance = calculator.calculateDistanceKm(6.9344, 79.8428, 6.9344, 79.8428);
        assertEquals(0.0, distance, 0.001);
    }
}