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
        try (Connection conn = getConnection()) {
            if (tableExists(conn)) {
                return;
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE orders (
                        id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        customer_name VARCHAR2(255) NOT NULL,
                        product VARCHAR2(255) NOT NULL,
                        quantity NUMBER(10) NOT NULL,
                        total_amount NUMBER(12, 2) NOT NULL,
                        status VARCHAR2(50) DEFAULT 'PENDING' NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
                    )
                    """);
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to initialize orders schema", e);
        }
    }

    public List<Order> findAll() {
        List<Order> orders = new ArrayList<>();
        String sql = """
            SELECT id, customer_name, product, quantity, total_amount, status, created_at
            FROM orders
            ORDER BY created_at DESC
            """;
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
            """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, new String[]{"id"})) {
            ps.setString(1, customerName);
            ps.setString(2, product);
            ps.setInt(3, quantity);
            ps.setBigDecimal(4, totalAmount);
            ps.setString(5, OrderStatus.PENDING);
            int inserted = ps.executeUpdate();
            if (inserted == 0) {
                throw new IllegalStateException("Insert did not affect any rows");
            }
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    return findById(id).orElseThrow(() -> new IllegalStateException("Inserted order not found"));
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to insert order", e);
            throw new IllegalStateException("Failed to insert order", e);
        }
        throw new IllegalStateException("Insert did not return order id");
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
        String sql = "UPDATE orders SET status = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, id);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new OrderNotFoundException(id);
            }
            return findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to update order " + id, e);
            throw new IllegalStateException("Failed to update order", e);
        }
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
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM DUAL");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Database health check failed", e);
            return false;
        }
    }

    private boolean tableExists(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM user_tables WHERE table_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "ORDERS");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
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
