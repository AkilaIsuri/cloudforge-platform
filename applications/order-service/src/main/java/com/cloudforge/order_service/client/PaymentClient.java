package com.cloudforge.order_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class PaymentClient {

    private final RestClient restClient;

    public PaymentClient(RestClient.Builder builder, @Value("${services.payment-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public PaymentDto pay(Long orderId, BigDecimal amount, String method) {
        return restClient.post().uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PaymentRequest(orderId, amount, method))
                .retrieve().body(PaymentDto.class);
    }
}
