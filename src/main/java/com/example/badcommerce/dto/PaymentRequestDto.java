package com.example.badcommerce.dto;

import lombok.Data;

@Data
public class PaymentRequestDto {
    private Long orderId;
    private Long customerId;
    private double amount;
    private String paymentMethod;
}

