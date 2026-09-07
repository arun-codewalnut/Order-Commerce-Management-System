package com.example.commerce.order.web;

public record OrderItemResponse(Long id, Long productId, int quantity, double unitPrice, double totalPrice) {
}
