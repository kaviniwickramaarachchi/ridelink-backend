package com.ridelink.farepayment.controller;

import com.ridelink.farepayment.dto.ReceiptResponse;
import com.ridelink.farepayment.entity.Payment;
import com.ridelink.farepayment.entity.Receipt;
import com.ridelink.farepayment.exception.PassengerOwnershipException;
import com.ridelink.farepayment.security.CurrentUserProvider;
import com.ridelink.farepayment.service.PaymentService;
import com.ridelink.farepayment.service.ReceiptService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;
    private final PaymentService paymentService;
    private final CurrentUserProvider currentUserProvider;

    public ReceiptController(
            ReceiptService receiptService,
            PaymentService paymentService,
            CurrentUserProvider currentUserProvider) {
        this.receiptService = receiptService;
        this.paymentService = paymentService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/payment/{paymentId}")
    public ResponseEntity<ReceiptResponse> getReceipt(
            @PathVariable Long paymentId) {

        Payment payment = paymentService.getPaymentById(paymentId);

        if (!currentUserProvider.hasRole("ADMIN")) {
            Long currentUserId = currentUserProvider.getCurrentUserId();

            if (currentUserId == null ||
                    !currentUserId.equals(payment.getPassengerId())) {
                throw new PassengerOwnershipException(
                        "You are not authorized to access this receipt");
            }
        }

        Receipt receipt = receiptService.getReceiptByPaymentId(paymentId);

        return ResponseEntity.ok(ReceiptResponse.from(receipt));
    }
}