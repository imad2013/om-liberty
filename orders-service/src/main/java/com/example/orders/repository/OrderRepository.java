package com.example.orders.repository;

import com.example.orders.config.OrdersConfig;
import com.example.orders.model.Order;
import com.example.orders.model.OrderNotFoundException;
import com.example.orders.model.OrderStatus;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class OrderRepository {

    private static final Logger LOG = Logger.getLogger(OrderRepository.class.getName());

    @Inject
    OrdersConfig ordersConfig;

    @PostConstruct
    void initSchema() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS orders (
                    id BIGSERIAL PRIMARY KEY,
                    customer_name VARCHAR(255) NOT NULL,
                    product VARCHAR(255) NOT NULL,
                    quantity INT NOT NULL,
                    total_amount NUMERIC(12, 2) NOT NULL,
                    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to initialize orders schema", e);
        }
    }

    public List<Order> findAll() {
        List<Order> orders = new ArrayList<>();
        String sql = "SELECT id, customer_name, product, quantity, total_amount, status, created_at FROM orders ORDER BY created_at DESC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                orders.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to list orders", e);
            throw new IllegalStateException("Failed to list orders", e);
        }
        return orders;
    }

    public Order insert(String customerName, String product, int quantity, BigDecimal totalAmount) {
        String sql = """
            INSERT INTO orders (customer_name, product, quantity, total_amount, status)
            VALUES (?, ?, ?, ?, ?)
            RETURNING id, customer_name, product, quantity, total_amount, status, created_at
            """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customerName);
            ps.setString(2, product);
            ps.setInt(3, quantity);
            ps.setBigDecimal(4, totalAmount);
            ps.setString(5, OrderStatus.PENDING);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to insert order", e);
            throw new IllegalStateException("Failed to insert order", e);
        }
        throw new IllegalStateException("Insert did not return order");
    }

    public Optional<Order> findById(long id) {
        String sql = """
            SELECT id, customer_name, product, quantity, total_amount, status, created_at
            FROM orders
            WHERE id = ?
            """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to find order " + id, e);
            throw new IllegalStateException("Failed to find order", e);
        }
        return Optional.empty();
    }

    public Order updateStatus(long id, String status) {
        String sql = """
            UPDATE orders
            SET status = ?
            WHERE id = ?
            RETURNING id, customer_name, product, quantity, total_amount, status, created_at
            """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to update order " + id, e);
            throw new IllegalStateException("Failed to update order", e);
        }
        throw new OrderNotFoundException(id);
    }

    public void deleteById(long id) {
        String sql = "DELETE FROM orders WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            int deleted = ps.executeUpdate();
            if (deleted == 0) {
                throw new OrderNotFoundException(id);
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to delete order " + id, e);
            throw new IllegalStateException("Failed to delete order", e);
        }
    }

    public boolean isDatabaseReachable() {
        try (Connection conn = getConnection()) {
            return conn.isValid(3);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Database health check failed", e);
            return false;
        }
    }

    private Order mapRow(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Instant instant = createdAt != null ? createdAt.toInstant() : Instant.now();
        return new Order(
                rs.getLong("id"),
                rs.getString("customer_name"),
                rs.getString("product"),
                rs.getInt("quantity"),
                rs.getBigDecimal("total_amount"),
                rs.getString("status"),
                instant
        );
    }

    private Connection getConnection() throws SQLException {
        return java.sql.DriverManager.getConnection(
                ordersConfig.dbUrl(),
                ordersConfig.dbUser(),
                ordersConfig.dbPassword()
        );
    }
}
