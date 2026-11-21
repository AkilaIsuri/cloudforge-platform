package com.cloudforge.order_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class ProductClient {

    private final RestClient restClient;

    public ProductClient(RestClient.Builder builder, @Value("${services.product-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public ProductDto getProduct(Long productId) {
        return restClient.get().uri("/products/{id}", productId).retrieve().body(ProductDto.class);
    }

    public void reserve(Long productId, int quantity) {
        restClient.post().uri("/products/{id}/reserve", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", quantity))
                .retrieve().toBodilessEntity();
    }

    public void release(Long productId, int quantity) {
        restClient.post().uri("/products/{id}/release", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", quantity))
                .retrieve().toBodilessEntity();
    }
}
