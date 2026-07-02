package com.example.orders.service;

import com.example.orders.config.OrdersConfig;
import com.example.orders.messaging.OrderEventMessaging;
import com.example.orders.model.CreateOrderRequest;
import com.example.orders.model.Order;
import com.example.orders.model.OrderNotFoundException;
import com.example.orders.model.OrderStateException;
import com.example.orders.model.OrderStatus;
import com.example.orders.repository.OrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
public class OrderService {

    @Inject
    OrderRepository repository;

    @Inject
    OrderEventMessaging messaging;

    @Inject
    OrdersConfig ordersConfig;

    public List<Order> listOrders() {
        return repository.findAll();
    }

    public Order createOrder(CreateOrderRequest request) {
        BigDecimal unitPrice = request.getUnitPrice() != null ? request.getUnitPrice() : BigDecimal.TEN;
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));
        Order order = repository.insert(
                request.getCustomerName(),
                request.getProduct(),
                request.getQuantity(),
                total
        );
        messaging.publishOrderCreated(order);
        return order;
    }

    public Order fulfillOrder(long id) {
        Order order = repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        if (!OrderStatus.canFulfill(order.getStatus())) {
            throw new OrderStateException("Only pending orders can be fulfilled");
        }
        Order fulfilled = repository.updateStatus(id, OrderStatus.FULFILLED);
        messaging.publishOrderFulfilled(fulfilled);
        return fulfilled;
    }

    public void deleteOrder(long id) {
        Order order = repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        if (!OrderStatus.canDelete(order.getStatus())) {
            throw new OrderStateException("Only pending orders can be deleted");
        }
        repository.deleteById(id);
        messaging.publishOrderDeleted(order);
    }

    public String getServiceVersion() {
        return ordersConfig.serviceVersion();
    }

    public boolean isReady() {
        return repository.isDatabaseReachable() && messaging.isKafkaReachable();
    }
}
