package com.example.commerce.payment.web;

public record PaymentRequestDto(Long orderId, Long customerId, double amount, String paymentMethod) {
}
