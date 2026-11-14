package com.ridelink.farepayment.service;

import com.ridelink.farepayment.entity.Receipt;
import com.ridelink.farepayment.exception.ReceiptNotFoundException;
import com.ridelink.farepayment.repository.ReceiptRepository;
import org.springframework.stereotype.Service;

@Service
public class ReceiptService {

    private final ReceiptRepository receiptRepository;

    public ReceiptService(ReceiptRepository receiptRepository) {
        this.receiptRepository = receiptRepository;
    }

    public Receipt saveReceipt(Receipt receipt) {
        receipt.setIssuedAt(java.time.LocalDateTime.now());
        return receiptRepository.save(receipt);
    }

    public Receipt getReceiptByPaymentId(Long paymentId) {
        return receiptRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ReceiptNotFoundException(
                        "No receipt found for payment id " + paymentId));
    }
}