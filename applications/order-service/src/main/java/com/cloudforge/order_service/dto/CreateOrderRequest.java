package com.cloudforge.order_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateOrderRequest(
        @NotNull Long userId,
        @NotEmpty List<@Valid Item> items,
        String paymentMethod) {

    public record Item(@NotNull Long productId, @Min(1) int quantity) {
    }
}
