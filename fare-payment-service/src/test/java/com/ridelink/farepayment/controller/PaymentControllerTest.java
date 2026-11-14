package com.ridelink.farepayment.controller;

import com.ridelink.farepayment.dto.PaymentRequest;
import com.ridelink.farepayment.entity.Payment;
import com.ridelink.farepayment.entity.PaymentMethod;
import com.ridelink.farepayment.exception.PassengerOwnershipException;
import com.ridelink.farepayment.security.CurrentUserProvider;
import com.ridelink.farepayment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private PaymentController controller;

    @BeforeEach
    void setUp() {
        controller = new PaymentController(
                paymentService,
                currentUserProvider
        );
    }

    @Test
    void recordPayment_usesAuthenticatedPassengerId() {

        when(currentUserProvider.getCurrentUserId())
                .thenReturn(42L);

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setRideId(10L);
        payment.setPassengerId(42L);
        payment.setMethod(PaymentMethod.CASH);

        when(paymentService.recordPayment(
                10L,
                42L,
                PaymentMethod.CASH,
                null
        )).thenReturn(payment);

        PaymentRequest request =
                new PaymentRequest(
                        10L,
                        PaymentMethod.CASH,
                        null
                );

        var response = controller.recordPayment(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(42L, response.getBody().passengerId());
    }

    @Test
    void getPayment_whenOwnedByCaller_isAllowed() {

        when(currentUserProvider.hasRole("ADMIN"))
                .thenReturn(false);

        when(currentUserProvider.getCurrentUserId())
                .thenReturn(42L);

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setRideId(10L);
        payment.setPassengerId(42L);
        payment.setMethod(PaymentMethod.CASH);

        when(paymentService.getPaymentById(1L))
                .thenReturn(payment);

        var response = controller.getPayment(1L);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getPayment_whenNotOwnedByCaller_throwsOwnershipException() {

        when(currentUserProvider.hasRole("ADMIN"))
                .thenReturn(false);

        when(currentUserProvider.getCurrentUserId())
                .thenReturn(99L);

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setRideId(10L);
        payment.setPassengerId(42L);
        payment.setMethod(PaymentMethod.CASH);

        when(paymentService.getPaymentById(1L))
                .thenReturn(payment);

        assertThrows(
                PassengerOwnershipException.class,
                () -> controller.getPayment(1L)
        );
    }

    @Test
    void getPayment_whenCallerIsAdmin_bypassesOwnershipCheck() {

        when(currentUserProvider.hasRole("ADMIN"))
                .thenReturn(true);

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setRideId(10L);
        payment.setPassengerId(42L);
        payment.setMethod(PaymentMethod.CASH);

        when(paymentService.getPaymentById(1L))
                .thenReturn(payment);

        var response = controller.getPayment(1L);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getPaymentByRide_whenOwnedByCaller_isAllowed() {

        when(currentUserProvider.hasRole("ADMIN"))
                .thenReturn(false);

        when(currentUserProvider.getCurrentUserId())
                .thenReturn(42L);

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setRideId(10L);
        payment.setPassengerId(42L);
        payment.setMethod(PaymentMethod.CASH);

        when(paymentService.getPaymentByRideId(10L))
                .thenReturn(payment);

        var response = controller.getPaymentByRide(10L);

        assertEquals(200, response.getStatusCode().value());
    }
}