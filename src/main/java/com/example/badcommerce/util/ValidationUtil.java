package com.example.badcommerce.util;

import com.example.badcommerce.entity.Customer;
import com.example.badcommerce.entity.Product;

public class ValidationUtil {

    // Duplicated validation logic outside of Bean Validation
    public static void validateCustomer(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("Customer cannot be null");
        }
        if (customer.getName() == null || customer.getName().trim().length() < 2) {
            throw new IllegalArgumentException("Customer name is too short");
        }
        if (customer.getEmail() == null || !customer.getEmail().contains("@")) {
            throw new IllegalArgumentException("Customer email is invalid");
        }
    }

    public static void validateProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("Product cannot be null");
        }
        if (product.getPrice() <= 0.0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
    }
}

