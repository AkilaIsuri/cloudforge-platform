package com.cloudforge.payment_service.service;

import com.cloudforge.payment_service.dto.PaymentRequest;
import com.cloudforge.payment_service.entity.Payment;
import com.cloudforge.payment_service.entity.PaymentStatus;
import com.cloudforge.payment_service.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, new BigDecimal("5000"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void approvesPayment_atOrBelowLimit() {
        Payment payment = paymentService.processPayment(new PaymentRequest(1L, new BigDecimal("5000.00"), "CARD"));

        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
        assertNotNull(payment.getTransactionRef());
    }

    @Test
    void declinesPayment_aboveLimit() {
        Payment payment = paymentService.processPayment(new PaymentRequest(2L, new BigDecimal("5000.01"), null));

        assertEquals(PaymentStatus.DECLINED, payment.getStatus());
        assertNull(payment.getTransactionRef());
        assertEquals("CARD", payment.getMethod());
    }
}
