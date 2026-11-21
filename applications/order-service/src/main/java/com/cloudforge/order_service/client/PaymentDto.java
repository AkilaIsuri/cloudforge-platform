package com.cloudforge.order_service.client;

public record PaymentDto(Long id, String status, String reason) {
}
