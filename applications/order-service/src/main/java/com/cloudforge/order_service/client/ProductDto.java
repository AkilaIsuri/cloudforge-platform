package com.cloudforge.order_service.client;

import java.math.BigDecimal;

public record ProductDto(Long id, String name, BigDecimal price, Integer stock) {
}
