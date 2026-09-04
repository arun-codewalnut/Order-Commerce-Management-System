package com.example.badcommerce.util;

public class PriceUtil {

    // Anti-pattern: using double for money arithmetic
    public static double multiply(double price, int quantity) {
        return price * quantity;
    }

    public static double addTax(double amount, double taxRate) {
        return amount + (amount * taxRate);
    }

    public static double roundToTwoDecimals(double val) {
        // Primitive rounding with double math issues
        return Math.round(val * 100.0) / 100.0;
    }
}

