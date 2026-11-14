package com.ridelink.farepayment.dto;

import com.ridelink.farepayment.entity.Receipt;

import java.time.LocalDateTime;

public record ReceiptResponse(
        Long id,
        Long paymentId,
        String receiptNumber,
        Double amount,
        LocalDateTime issuedAt
) {
    public static ReceiptResponse from(Receipt receipt) {
        return new ReceiptResponse(
                receipt.getId(),
                receipt.getPaymentId(),
                receipt.getReceiptNumber(),
                receipt.getAmount(),
                receipt.getIssuedAt()
        );
    }
}