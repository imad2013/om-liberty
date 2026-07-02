package com.example.orders.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;

@ApplicationScoped
public class OrdersConfig {

    @Inject
    Config config;

    public String dbUrl() {
        return value("orders.db.url", "ORDERS_DB_URL", "jdbc:oracle:thin:@//oracle:1521/devdb");
    }

    public String dbUser() {
        return value("orders.db.user", "ORDERS_DB_USER", "dbadmin");
    }

    public String dbPassword() {
        return value("orders.db.password", "ORDERS_DB_PASSWORD", "dbadmin123");
    }

    public String kafkaBootstrapServers() {
        return value("orders.kafka.bootstrap.servers", "ORDERS_KAFKA_BOOTSTRAP_SERVERS", "kafka:9092");
    }

    public String kafkaTopic() {
        return value("orders.kafka.topic", "ORDERS_KAFKA_TOPIC", "orders-events");
    }

    public String serviceVersion() {
        return value("orders.service.version", "ORDERS_SERVICE_VERSION", "v1");
    }

    public boolean securityEnabled() {
        return config.getOptionalValue("orders.security.enabled", Boolean.class)
                .or(() -> config.getOptionalValue("ORDERS_SECURITY_ENABLED", Boolean.class))
                .orElseGet(() -> {
                    String env = System.getenv("ORDERS_SECURITY_ENABLED");
                    return env != null && Boolean.parseBoolean(env);
                });
    }

    private String value(String property, String envKey, String defaultValue) {
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return config.getOptionalValue(property, String.class)
                .or(() -> config.getOptionalValue(envKey, String.class))
                .orElse(defaultValue);
    }
}
