package com.ridelink.farepayment.service;

import com.ridelink.farepayment.entity.FinalFare;
import com.ridelink.farepayment.entity.Payment;
import com.ridelink.farepayment.entity.PaymentMethod;
import com.ridelink.farepayment.entity.PaymentStatus;
import com.ridelink.farepayment.exception.DuplicatePaymentException;
import com.ridelink.farepayment.exception.PassengerOwnershipException;
import com.ridelink.farepayment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ReceiptService receiptService;

    @Mock
    private FinalFareService finalFareService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository,
                receiptService,
                finalFareService
        );
    }

    @Test
    void recordPayment_whenSuccessful_savesPaymentAndCreatesReceipt() {

        when(paymentRepository.existsByRideIdAndStatus(
                10L, PaymentStatus.SUCCESS))
                .thenReturn(false);

        FinalFare finalFare = new FinalFare();
        finalFare.setRideId(10L);
        finalFare.setPassengerId(42L);
        finalFare.setAmount(1000.0);

        when(finalFareService.getFinalFareByRideId(10L))
                .thenReturn(finalFare);

        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment payment = invocation.getArgument(0);
                    payment.setId(1L);
                    return payment;
                });

        Payment result = paymentService.recordPayment(
                10L,
                42L,
                PaymentMethod.CASH,
                null
        );

        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
        assertEquals(42L, result.getPassengerId());
        assertEquals(1000.0, result.getAmount(), 0.01);

        verify(paymentRepository)
                .saveAndFlush(any(Payment.class));

        verify(receiptService)
                .saveReceipt(any());
    }

    @Test
    void recordPayment_whenCardFailure_savesFailedPaymentWithoutReceipt() {

        when(paymentRepository.existsByRideIdAndStatus(
                20L, PaymentStatus.SUCCESS))
                .thenReturn(false);

        FinalFare finalFare = new FinalFare();
        finalFare.setRideId(20L);
        finalFare.setPassengerId(42L);
        finalFare.setAmount(1500.0);

        when(finalFareService.getFinalFareByRideId(20L))
                .thenReturn(finalFare);

        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment payment = invocation.getArgument(0);
                    payment.setId(2L);
                    return payment;
                });

        Payment result = paymentService.recordPayment(
                20L,
                42L,
                PaymentMethod.CARD,
                "FAIL_TEST"
        );

        assertEquals(PaymentStatus.FAILED, result.getStatus());

        verify(paymentRepository)
                .saveAndFlush(any(Payment.class));

        verify(receiptService, never())
                .saveReceipt(any());
    }

    @Test
    void recordPayment_afterFailedAttempt_allowsSuccessfulRetry() {

        when(paymentRepository.existsByRideIdAndStatus(
                30L, PaymentStatus.SUCCESS))
                .thenReturn(false);

        FinalFare finalFare = new FinalFare();
        finalFare.setRideId(30L);
        finalFare.setPassengerId(42L);
        finalFare.setAmount(2000.0);

        when(finalFareService.getFinalFareByRideId(30L))
                .thenReturn(finalFare);

        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment payment = invocation.getArgument(0);

                    if (payment.getStatus() == PaymentStatus.FAILED) {
                        payment.setId(3L);
                    } else {
                        payment.setId(4L);
                    }

                    return payment;
                });

        Payment failedPayment = paymentService.recordPayment(
                30L,
                42L,
                PaymentMethod.CARD,
                "FAIL_TEST"
        );

        Payment successfulPayment = paymentService.recordPayment(
                30L,
                42L,
                PaymentMethod.CARD,
                "VALID_TOKEN"
        );

        assertEquals(
                PaymentStatus.FAILED,
                failedPayment.getStatus()
        );

        assertEquals(
                PaymentStatus.SUCCESS,
                successfulPayment.getStatus()
        );

        verify(paymentRepository, times(2))
                .saveAndFlush(any(Payment.class));

        verify(receiptService, times(1))
                .saveReceipt(any());
    }

    @Test
    void recordPayment_whenSuccessfulPaymentAlreadyExists_throwsDuplicatePaymentException() {

        when(paymentRepository.existsByRideIdAndStatus(
                40L, PaymentStatus.SUCCESS))
                .thenReturn(true);

        assertThrows(
                DuplicatePaymentException.class,
                () -> paymentService.recordPayment(
                        40L,
                        42L,
                        PaymentMethod.CASH,
                        null
                )
        );

        verify(paymentRepository, never())
                .saveAndFlush(any(Payment.class));

        verify(receiptService, never())
                .saveReceipt(any());
    }

    @Test
    void recordPayment_whenDatabaseRejectsConcurrentSuccessfulPayment_throwsDuplicatePaymentException() {

        when(paymentRepository.existsByRideIdAndStatus(
                50L, PaymentStatus.SUCCESS))
                .thenReturn(false);

        FinalFare finalFare = new FinalFare();
        finalFare.setRideId(50L);
        finalFare.setPassengerId(42L);
        finalFare.setAmount(2500.0);

        when(finalFareService.getFinalFareByRideId(50L))
                .thenReturn(finalFare);

        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "Duplicate successful payment"
                        )
                );

        assertThrows(
                DuplicatePaymentException.class,
                () -> paymentService.recordPayment(
                        50L,
                        42L,
                        PaymentMethod.CASH,
                        null
                )
        );

        verify(receiptService, never())
                .saveReceipt(any());
    }
    @Test
void recordPayment_whenPassengerDoesNotOwnRide_throwsPassengerOwnershipException() {

    when(paymentRepository.existsByRideIdAndStatus(
            60L, PaymentStatus.SUCCESS))
            .thenReturn(false);

    FinalFare finalFare = new FinalFare();
    finalFare.setRideId(60L);
    finalFare.setPassengerId(42L);
    finalFare.setAmount(3000.0);

    when(finalFareService.getFinalFareByRideId(60L))
            .thenReturn(finalFare);

    assertThrows(
            PassengerOwnershipException.class,
            () -> paymentService.recordPayment(
                    60L,
                    99L,
                    PaymentMethod.CASH,
                    null
            )
    );

    verify(paymentRepository, never())
            .saveAndFlush(any(Payment.class));

    verify(receiptService, never())
            .saveReceipt(any());
}
}