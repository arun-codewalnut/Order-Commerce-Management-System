package com.example.commerce.customer.internal;

final class CustomerValidator {

    private CustomerValidator() {
    }

    static void validate(Customer customer) {
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
}
