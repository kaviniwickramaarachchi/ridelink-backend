package com.ridelink.farepayment.controller;

import com.ridelink.farepayment.dto.PaymentRequest;
import com.ridelink.farepayment.dto.PaymentResponse;
import com.ridelink.farepayment.entity.Payment;
import com.ridelink.farepayment.exception.PassengerOwnershipException;
import com.ridelink.farepayment.security.CurrentUserProvider;
import com.ridelink.farepayment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUserProvider currentUserProvider;

    public PaymentController(PaymentService paymentService, CurrentUserProvider currentUserProvider) {
        this.paymentService = paymentService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> recordPayment(@Valid @RequestBody PaymentRequest request) {
        Long passengerId = currentUserProvider.getCurrentUserId();

        Payment payment = paymentService.recordPayment(
                request.rideId(),  passengerId,request.method(), request.cardToken());
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long id) {
        Payment payment = paymentService.getPaymentById(id);
        enforceOwnership(payment.getPassengerId());
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    @GetMapping("/ride/{rideId}")
    public ResponseEntity<PaymentResponse> getPaymentByRide(@PathVariable Long rideId) {
        Payment payment = paymentService.getPaymentByRideId(rideId);
        enforceOwnership(payment.getPassengerId());
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    private void enforceOwnership(Long passengerId) {
        if (currentUserProvider.hasRole("ADMIN")) {
            return;
        }
        Long currentUserId = currentUserProvider.getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(passengerId)) {
            throw new PassengerOwnershipException(
                    "You are not authorized to access payment data for passenger id " + passengerId);
        }
    }
}