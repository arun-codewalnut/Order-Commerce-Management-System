package com.example.commerce.order.web;

import com.example.commerce.order.internal.Order;

import java.util.List;

public record OrderResponse(Long id, Long customerId, String status, double totalAmount,
                            List<OrderItemResponse> items) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getStatus(), order.getTotalAmount(),
                order.getItems().stream()
                        .map(item -> new OrderItemResponse(item.getId(), item.getProductId(), item.getQuantity(),
                                item.getUnitPrice(), item.getTotalPrice()))
                        .toList());
    }
}
