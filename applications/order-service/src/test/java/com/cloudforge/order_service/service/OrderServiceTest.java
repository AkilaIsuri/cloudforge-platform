package com.cloudforge.order_service.service;

import com.cloudforge.order_service.client.PaymentClient;
import com.cloudforge.order_service.client.PaymentDto;
import com.cloudforge.order_service.client.ProductClient;
import com.cloudforge.order_service.client.ProductDto;
import com.cloudforge.order_service.client.UserClient;
import com.cloudforge.order_service.dto.CreateOrderRequest;
import com.cloudforge.order_service.entity.Order;
import com.cloudforge.order_service.entity.OrderStatus;
import com.cloudforge.order_service.repository.OrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private UserClient userClient;
    @Mock private ProductClient productClient;
    @Mock private PaymentClient paymentClient;

    /** Real in-memory metrics registry, so tests can check the business counters */
    private SimpleMeterRegistry meterRegistry;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        orderService = new OrderService(orderRepository, userClient, productClient, paymentClient, meterRegistry);

        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(userClient.userExists(1L)).thenReturn(true);
        lenient().when(productClient.getProduct(10L))
                .thenReturn(new ProductDto(10L, "Headphones", new BigDecimal("100.00"), 50));
        lenient().when(productClient.getProduct(20L))
                .thenReturn(new ProductDto(20L, "Laptop", new BigDecimal("1299.00"), 2));
    }

    private CreateOrderRequest request(CreateOrderRequest.Item... items) {
        return new CreateOrderRequest(1L, List.of(items), "CARD");
    }

    @Test
    void placeOrder_isPaid_whenPaymentApproved() {
        when(paymentClient.pay(any(), any(), eq("CARD"))).thenReturn(new PaymentDto(7L, "APPROVED", null));

        Order order = orderService.placeOrder(request(new CreateOrderRequest.Item(10L, 2)));

        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(0, new BigDecimal("200.00").compareTo(order.getTotalAmount()));
        assertEquals(7L, order.getPaymentId());
        verify(productClient).reserve(10L, 2);
        verify(productClient, never()).release(anyLong(), anyInt());

        assertEquals(1.0, meterRegistry.get("voltcart.orders").tag("status", "PAID").counter().count());
        assertEquals(200.0, meterRegistry.get("voltcart.revenue").counter().count());
    }

    @Test
    void placeOrder_releasesStock_whenPaymentDeclined() {
        when(paymentClient.pay(any(), any(), any())).thenReturn(new PaymentDto(8L, "DECLINED", "Too much"));

        Order order = orderService.placeOrder(request(new CreateOrderRequest.Item(10L, 3)));

        assertEquals(OrderStatus.PAYMENT_FAILED, order.getStatus());
        assertEquals("Too much", order.getFailureReason());
        verify(productClient).release(10L, 3);
    }

    @Test
    void placeOrder_rollsBackEarlierItems_whenLaterItemOutOfStock() {
        doNothing().when(productClient).reserve(10L, 1);
        doThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, new byte[0], null))
                .when(productClient).reserve(20L, 5);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> orderService.placeOrder(
                request(new CreateOrderRequest.Item(10L, 1), new CreateOrderRequest.Item(20L, 5))));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(productClient).release(10L, 1);
        verify(orderRepository, never()).save(any());
        verify(paymentClient, never()).pay(any(), any(), any());
    }

    @Test
    void placeOrder_rejected_whenUserMissing() {
        when(userClient.userExists(2L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> orderService.placeOrder(
                new CreateOrderRequest(2L, List.of(new CreateOrderRequest.Item(10L, 1)), null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(productClient, never()).reserve(anyLong(), anyInt());
    }
}
