package com.cloudforge.payment_service.service;

import com.cloudforge.payment_service.dto.PaymentRequest;
import com.cloudforge.payment_service.entity.Payment;
import com.cloudforge.payment_service.entity.PaymentStatus;
import com.cloudforge.payment_service.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Mock payment provider. No real money moves: payments up to the configured
 * limit are approved, anything above it is declined.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BigDecimal maxApprovedAmount;

    public PaymentService(PaymentRepository paymentRepository,
                          @Value("${payment.max-approved-amount}") BigDecimal maxApprovedAmount) {
        this.paymentRepository = paymentRepository;
        this.maxApprovedAmount = maxApprovedAmount;
    }

    public Payment processPayment(PaymentRequest request) {
        String method = (request.method() == null || request.method().isBlank()) ? "CARD" : request.method();
        Payment payment = new Payment(request.orderId(), request.amount(), method);

        if (request.amount().compareTo(maxApprovedAmount) > 0) {
            payment.setStatus(PaymentStatus.DECLINED);
            payment.setReason("Amount exceeds the demo payment limit of " + maxApprovedAmount);
        } else {
            payment.setStatus(PaymentStatus.APPROVED);
            payment.setTransactionRef("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        return paymentRepository.save(payment);
    }

    public List<Payment> getPayments() {
        return paymentRepository.findAll();
    }

    public Payment getPayment(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment " + id + " not found"));
    }

    public List<Payment> getPaymentsForOrder(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }
}
