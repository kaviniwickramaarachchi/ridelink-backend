package com.ridelink.farepayment.service;

import com.ridelink.farepayment.config.FareProperties;
import com.ridelink.farepayment.entity.FinalFare;
import com.ridelink.farepayment.exception.FinalFareConflictException;
import com.ridelink.farepayment.exception.FinalFareNotFoundException;
import com.ridelink.farepayment.repository.FinalFareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinalFareServiceTest {

    @Mock
    private FinalFareRepository finalFareRepository;

    private FinalFareService finalFareService;

    @BeforeEach
    void setUp() {
        FareProperties props = new FareProperties();
        props.setBaseFare(100);
        props.setPerKmRate(80);

        FareCalculator calculator = new DistanceFareCalculator(props);

        finalFareService =
                new FinalFareService(finalFareRepository, calculator);
    }

    @Test
    void getOrCreateFinalFare_whenNoneExists_calculatesAndSaves() {

        when(finalFareRepository.findByRideId(1L))
                .thenReturn(Optional.empty());

        when(finalFareRepository.save(any(FinalFare.class)))
                .thenAnswer(invocation -> {
                    FinalFare fare = invocation.getArgument(0);
                    fare.setId(10L);
                    return fare;
                });

        FinalFare result =
                finalFareService.getOrCreateFinalFare(
                        1L,
                        42L,
                        10.0,
                        15.0
                );

        // 100 + (80 * 10) = 900
        assertEquals(900.0, result.getAmount(), 0.01);

        assertEquals(1L, result.getRideId());
        assertEquals(42L, result.getPassengerId());
        assertEquals(10.0, result.getDistanceKm(), 0.01);
        assertEquals(15.0, result.getDurationMinutes(), 0.01);

        verify(finalFareRepository)
                .save(any(FinalFare.class));
    }

    @Test
    void getOrCreateFinalFare_calledAgainWithSameInputs_returnsSameStoredResult() {

        FinalFare existing = new FinalFare();

        existing.setRideId(2L);
        existing.setPassengerId(42L);
        existing.setDistanceKm(10.0);
        existing.setDurationMinutes(15.0);
        existing.setAmount(900.0);

        when(finalFareRepository.findByRideId(2L))
                .thenReturn(Optional.of(existing));

        FinalFare result =
                finalFareService.getOrCreateFinalFare(
                        2L,
                        42L,
                        10.0,
                        15.0
                );

        assertEquals(900.0, result.getAmount());
        assertEquals(42L, result.getPassengerId());

        verify(finalFareRepository, never())
                .save(any(FinalFare.class));
    }

    @Test
    void getOrCreateFinalFare_calledAgainWithDifferentInputs_throwsConflict() {

        FinalFare existing = new FinalFare();

        existing.setRideId(3L);
        existing.setPassengerId(42L);
        existing.setDistanceKm(10.0);
        existing.setDurationMinutes(15.0);
        existing.setAmount(900.0);

        when(finalFareRepository.findByRideId(3L))
                .thenReturn(Optional.of(existing));

        assertThrows(
                FinalFareConflictException.class,
                () -> finalFareService.getOrCreateFinalFare(
                        3L,
                        42L,
                        20.0,
                        15.0
                )
        );
    }

    @Test
    void getFinalFareByRideId_whenNotFound_throwsNotFound() {

        when(finalFareRepository.findByRideId(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                FinalFareNotFoundException.class,
                () -> finalFareService.getFinalFareByRideId(999L)
        );
    }
}