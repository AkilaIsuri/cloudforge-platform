package com.cloudforge.product_service.controller;

import jakarta.validation.constraints.Min;

public record StockRequest(@Min(1) int quantity) {
}
