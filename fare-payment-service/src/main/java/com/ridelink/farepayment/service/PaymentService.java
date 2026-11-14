package com.ridelink.farepayment.service;

import com.ridelink.farepayment.entity.FinalFare;
import com.ridelink.farepayment.entity.Payment;
import com.ridelink.farepayment.entity.PaymentMethod;
import com.ridelink.farepayment.entity.PaymentStatus;
import com.ridelink.farepayment.entity.Receipt;
import com.ridelink.farepayment.exception.DuplicatePaymentException;
import com.ridelink.farepayment.exception.PaymentNotFoundException;
import com.ridelink.farepayment.exception.PassengerOwnershipException;
import com.ridelink.farepayment.repository.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {

    private static final String SIMULATED_FAILURE_TOKEN = "FAIL_TEST";

    private final PaymentRepository paymentRepository;
    private final ReceiptService receiptService;
    private final FinalFareService finalFareService;

    public PaymentService(
            PaymentRepository paymentRepository,
            ReceiptService receiptService,
            FinalFareService finalFareService) {

        this.paymentRepository = paymentRepository;
        this.receiptService = receiptService;
        this.finalFareService = finalFareService;
    }

    @Transactional
    public Payment recordPayment(
            Long rideId,
            Long passengerId,
            PaymentMethod method,
            String cardToken) {

        if (paymentRepository.existsByRideIdAndStatus(
                rideId, PaymentStatus.SUCCESS)) {

            throw new DuplicatePaymentException(
                    "Ride id " + rideId +
                            " already has a successful payment");
        }

        FinalFare finalFare =
        finalFareService.getFinalFareByRideId(rideId);

        if (!finalFare.getPassengerId().equals(passengerId)) {
            throw new PassengerOwnershipException(
              "Passenger does not own ride id " + rideId);
        } 

        
        Payment payment = new Payment();
        payment.setRideId(rideId);
        payment.setPassengerId(passengerId);
        payment.setAmount(finalFare.getAmount());
        payment.setMethod(method);

        boolean isSimulatedFailure =
                method == PaymentMethod.CARD
                        && SIMULATED_FAILURE_TOKEN.equals(cardToken);

        payment.setStatus(
                isSimulatedFailure
                        ? PaymentStatus.FAILED
                        : PaymentStatus.SUCCESS);

        Payment saved;

        try {
            saved = paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException ex) {

            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                throw new DuplicatePaymentException(
                        "Ride id " + rideId +
                                " already has a successful payment");
            }

            throw ex;
        }

        if (saved.getStatus() == PaymentStatus.SUCCESS) {

            Receipt receipt = new Receipt();
            receipt.setPaymentId(saved.getId());
            receipt.setAmount(saved.getAmount());
            receipt.setReceiptNumber(generateReceiptNumber());

            receiptService.saveReceipt(receipt);
        }

        return saved;
    }

    public Payment getPaymentById(Long id) {

        return paymentRepository.findById(id)
                .orElseThrow(() ->
                        new PaymentNotFoundException(
                                "No payment found with id " + id));
    }

    public Payment getPaymentByRideId(Long rideId) {

        return paymentRepository.findByRideId(rideId)
                .orElseThrow(() ->
                        new PaymentNotFoundException(
                                "No payment found for ride id " + rideId));
    }

    private String generateReceiptNumber() {

        return "RCPT-"
                + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
    }
}
