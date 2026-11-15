package com.cloudforge.order_service.client;

import java.math.BigDecimal;

public record PaymentRequest(Long orderId, BigDecimal amount, String method) {
}
