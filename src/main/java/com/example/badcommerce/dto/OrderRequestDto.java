package com.example.badcommerce.dto;

import lombok.Data;
import java.util.List;

@Data
public class OrderRequestDto {
    private Long customerId;
    private List<OrderItemDto> items;
    private String shippingCountry;

    @Data
    public static class OrderItemDto {
        private Long productId;
        private int quantity;
    }
}

