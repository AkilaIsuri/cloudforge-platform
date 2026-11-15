package com.cloudforge.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single entry point for the frontend:
 *   /api/users/**    -> user-service    /users/**
 *   /api/products/** -> product-service /products/**
 *   /api/orders/**   -> order-service   /orders/**
 *   /api/payments/** -> payment-service /payments/**
 */
@Configuration
public class RouteConfig {

    private static final String API_PREFIX = "/api/(?<segment>.*)";
    private static final String WITHOUT_PREFIX = "/${segment}";

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder,
                               @Value("${services.user-url}") String userUrl,
                               @Value("${services.product-url}") String productUrl,
                               @Value("${services.order-url}") String orderUrl,
                               @Value("${services.payment-url}") String paymentUrl) {
        return builder.routes()
                .route("user-service", r -> r.path("/api/users", "/api/users/**")
                        .filters(f -> f.rewritePath(API_PREFIX, WITHOUT_PREFIX))
                        .uri(userUrl))
                .route("product-service", r -> r.path("/api/products", "/api/products/**")
                        .filters(f -> f.rewritePath(API_PREFIX, WITHOUT_PREFIX))
                        .uri(productUrl))
                .route("order-service", r -> r.path("/api/orders", "/api/orders/**")
                        .filters(f -> f.rewritePath(API_PREFIX, WITHOUT_PREFIX))
                        .uri(orderUrl))
                .route("payment-service", r -> r.path("/api/payments", "/api/payments/**")
                        .filters(f -> f.rewritePath(API_PREFIX, WITHOUT_PREFIX))
                        .uri(paymentUrl))
                .build();
    }
}
