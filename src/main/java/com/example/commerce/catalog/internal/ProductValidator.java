package com.example.commerce.catalog.internal;

final class ProductValidator {

    private ProductValidator() {
    }

    static void validate(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("Product cannot be null");
        }
        if (product.getPrice() <= 0.0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
    }
}
