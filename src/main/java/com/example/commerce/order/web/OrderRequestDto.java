package com.example.commerce.order.web;

import java.util.List;

public record OrderRequestDto(Long customerId, List<OrderItemDto> items, String shippingCountry) {

    public record OrderItemDto(Long productId, int quantity) {
    }
}
