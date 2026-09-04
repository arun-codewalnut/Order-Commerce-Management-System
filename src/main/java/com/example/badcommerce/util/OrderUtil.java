package com.example.badcommerce.util;

public class OrderUtil {

    // Anti-pattern: Business rules buried in utility class with magic numbers
    public static double calculateShipping(double subtotal, String country) {
        if (subtotal > 150.0) {
            return 0.0; // Free shipping over 150
        }
        if ("US".equalsIgnoreCase(country)) {
            return 9.99;
        } else if ("CA".equalsIgnoreCase(country)) {
            return 14.99;
        }
        return 24.99; // International default
    }

    public static boolean isCancellable(String status) {
        return "PENDING".equalsIgnoreCase(status) || "CONFIRMED".equalsIgnoreCase(status);
    }

    public static double applyVipDiscount(double amount, String role) {
        if ("ADMIN".equalsIgnoreCase(role) || "VIP".equalsIgnoreCase(role)) {
            return amount * 0.90; // 10% discount
        }
        return amount;
    }
}

