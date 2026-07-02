package com.example.orders.health;

import com.example.orders.service.OrderService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class OrdersReadinessHealthCheck implements HealthCheck {

    @Inject
    OrderService orderService;

    @Override
    public HealthCheckResponse call() {
        boolean ready = orderService.isReady();
        return ready
                ? HealthCheckResponse.up("orders-service-ready")
                : HealthCheckResponse.down("orders-service-not-ready");
    }
}
