package com.cloudforge.payment_service.controller;

import com.cloudforge.payment_service.dto.PaymentRequest;
import com.cloudforge.payment_service.entity.Payment;
import com.cloudforge.payment_service.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** Always returns 201; check "status" (APPROVED / DECLINED) for the result. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Payment processPayment(@Valid @RequestBody PaymentRequest request) {
        return paymentService.processPayment(request);
    }

    @GetMapping
    public List<Payment> getPayments() {
        return paymentService.getPayments();
    }

    @GetMapping("/{id}")
    public Payment getPayment(@PathVariable Long id) {
        return paymentService.getPayment(id);
    }

    @GetMapping("/order/{orderId}")
    public List<Payment> getPaymentsForOrder(@PathVariable Long orderId) {
        return paymentService.getPaymentsForOrder(orderId);
    }
}
