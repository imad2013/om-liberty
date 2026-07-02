package com.example.orders.model;

public final class OrderStatus {

    public static final String PENDING = "PENDING";
    public static final String FULFILLED = "FULFILLED";

    private OrderStatus() {
    }

    public static boolean canFulfill(String status) {
        return PENDING.equals(status);
    }

    public static boolean canDelete(String status) {
        return PENDING.equals(status);
    }
}
