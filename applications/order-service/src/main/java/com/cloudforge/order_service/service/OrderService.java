package com.cloudforge.order_service.service;

import com.cloudforge.order_service.client.PaymentClient;
import com.cloudforge.order_service.client.PaymentDto;
import com.cloudforge.order_service.client.ProductClient;
import com.cloudforge.order_service.client.ProductDto;
import com.cloudforge.order_service.client.UserClient;
import com.cloudforge.order_service.dto.CreateOrderRequest;
import com.cloudforge.order_service.entity.Order;
import com.cloudforge.order_service.entity.OrderItem;
import com.cloudforge.order_service.entity.OrderStatus;
import com.cloudforge.order_service.repository.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Places an order across services:
 *   1. check the user exists        (user-service)
 *   2. reserve stock for each item  (product-service)
 *   3. save the order as PENDING
 *   4. take payment                 (payment-service)
 *   5. PAID, or PAYMENT_FAILED and give the reserved stock back
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final UserClient userClient;
    private final ProductClient productClient;
    private final PaymentClient paymentClient;

    // Business metrics, exposed on /actuator/prometheus as
    //   voltcart_orders_total{status="PAID"|"PAYMENT_FAILED"}  and  voltcart_revenue_total
    private final Counter paidOrders;
    private final Counter failedOrders;
    private final Counter revenue;

    public OrderService(OrderRepository orderRepository, UserClient userClient,
                        ProductClient productClient, PaymentClient paymentClient,
                        MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.userClient = userClient;
        this.productClient = productClient;
        this.paymentClient = paymentClient;
        // Registered up front so the series exist (at 0) before the first order
        this.paidOrders = Counter.builder("voltcart.orders").tag("status", OrderStatus.PAID.name())
                .description("Orders placed, by final status").register(meterRegistry);
        this.failedOrders = Counter.builder("voltcart.orders").tag("status", OrderStatus.PAYMENT_FAILED.name())
                .description("Orders placed, by final status").register(meterRegistry);
        this.revenue = Counter.builder("voltcart.revenue")
                .description("Total value of paid orders in USD").register(meterRegistry);
    }

    public Order placeOrder(CreateOrderRequest request) {
        ensureUserExists(request.userId());

        List<OrderItem> items = reserveItems(request.items());
        BigDecimal total = items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = orderRepository.save(new Order(request.userId(), items, total));

        String method = (request.paymentMethod() == null || request.paymentMethod().isBlank())
                ? "CARD" : request.paymentMethod();
        try {
            PaymentDto payment = paymentClient.pay(order.getId(), total, method);
            order.setPaymentId(payment.id());
            if ("APPROVED".equals(payment.status())) {
                order.setStatus(OrderStatus.PAID);
            } else {
                order.setStatus(OrderStatus.PAYMENT_FAILED);
                order.setFailureReason(payment.reason());
                releaseItems(items);
            }
        } catch (RestClientException ex) {
            log.error("Payment call failed for order {}", order.getId(), ex);
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            order.setFailureReason("Payment service unavailable");
            releaseItems(items);
        }
        Order saved = orderRepository.save(order);
        recordMetrics(saved);
        return saved;
    }

    private void recordMetrics(Order order) {
        if (order.getStatus() == OrderStatus.PAID) {
            paidOrders.increment();
            revenue.increment(order.getTotalAmount().doubleValue());
        } else if (order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            failedOrders.increment();
        }
    }

    public List<Order> getOrders(Long userId) {
        return userId == null
                ? orderRepository.findAllByOrderByCreatedAtDesc()
                : orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order " + id + " not found"));
    }

    private void ensureUserExists(Long userId) {
        boolean exists;
        try {
            exists = userClient.userExists(userId);
        } catch (RestClientException ex) {
            log.error("User service call failed", ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service unavailable");
        }
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User " + userId + " does not exist");
        }
    }

    /** Reserves every item, or none: if one fails, stock already taken is given back. */
    private List<OrderItem> reserveItems(List<CreateOrderRequest.Item> requested) {
        List<OrderItem> reserved = new ArrayList<>();
        for (CreateOrderRequest.Item item : requested) {
            try {
                ProductDto product = productClient.getProduct(item.productId());
                productClient.reserve(item.productId(), item.quantity());
                reserved.add(new OrderItem(product.id(), product.name(), product.price(), item.quantity()));
            } catch (HttpClientErrorException.NotFound ex) {
                releaseItems(reserved);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product " + item.productId() + " not found");
            } catch (HttpClientErrorException.Conflict ex) {
                releaseItems(reserved);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Not enough stock for product " + item.productId());
            } catch (RestClientException ex) {
                log.error("Product service call failed", ex);
                releaseItems(reserved);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product service unavailable");
            }
        }
        return reserved;
    }

    private void releaseItems(List<OrderItem> items) {
        for (OrderItem item : items) {
            try {
                productClient.release(item.getProductId(), item.getQuantity());
            } catch (RestClientException ex) {
                log.warn("Could not release {} x product {}", item.getQuantity(), item.getProductId(), ex);
            }
        }
    }
}
