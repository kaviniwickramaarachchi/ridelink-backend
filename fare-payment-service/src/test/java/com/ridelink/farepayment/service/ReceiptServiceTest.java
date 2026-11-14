package com.ridelink.farepayment.service;

import com.ridelink.farepayment.entity.Receipt;
import com.ridelink.farepayment.exception.ReceiptNotFoundException;
import com.ridelink.farepayment.repository.ReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    @Mock
    private ReceiptRepository receiptRepository;

    private ReceiptService receiptService;

    @BeforeEach
    void setUp() {
        receiptService = new ReceiptService(receiptRepository);
    }

    @Test
    void saveReceipt_setsIssuedAtAndPersists() {
        Receipt receipt = new Receipt();
        receipt.setPaymentId(1L);
        receipt.setReceiptNumber("RCPT-ABC12345");
        receipt.setAmount(900.0);

        when(receiptRepository.save(any(Receipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Receipt result = receiptService.saveReceipt(receipt);

        assertNotNull(result.getIssuedAt());

        ArgumentCaptor<Receipt> captor = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getPaymentId());
    }

    @Test
    void getReceiptByPaymentId_whenFound_returnsReceipt() {
        Receipt receipt = new Receipt();
        receipt.setPaymentId(5L);
        when(receiptRepository.findByPaymentId(5L)).thenReturn(Optional.of(receipt));

        Receipt result = receiptService.getReceiptByPaymentId(5L);

        assertEquals(5L, result.getPaymentId());
    }

    @Test
    void getReceiptByPaymentId_whenNotFound_throwsReceiptNotFoundException() {
        when(receiptRepository.findByPaymentId(999L)).thenReturn(Optional.empty());

        assertThrows(ReceiptNotFoundException.class, () ->
                receiptService.getReceiptByPaymentId(999L));
    }
}