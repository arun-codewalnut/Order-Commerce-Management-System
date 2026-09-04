package com.example.badcommerce.service;

import com.example.badcommerce.dto.PaymentRequestDto;
import com.example.badcommerce.entity.Customer;
import com.example.badcommerce.entity.Order;
import com.example.badcommerce.entity.Payment;
import com.example.badcommerce.repository.CustomerRepository;
import com.example.badcommerce.repository.OrderRepository;
import com.example.badcommerce.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ExternalPaymentClient externalPaymentClient;

    // Anti-pattern: Non-idempotent payment processing. Repeating requests create duplicate charges
    public Payment processPayment(PaymentRequestDto request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + request.getOrderId()));

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + request.getCustomerId()));

        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setCustomerId(customer.getId());
        payment.setAmount(request.getAmount());
        payment.setStatus("PENDING");
        payment = paymentRepository.save(payment);

        // Call external gateway directly
        Map<String, Object> gatewayResponse = externalPaymentClient.processPayment(
                order.getId(),
                request.getAmount(),
                customer.getEmail()
        );

        String status = (String) gatewayResponse.getOrDefault("status", "FAILED");
        String ref = (String) gatewayResponse.getOrDefault("reference", "REF-" + System.currentTimeMillis());

        payment.setStatus("SUCCESS".equalsIgnoreCase(status) ? "SUCCESS" : "FAILED");
        payment.setProviderReference(ref);

        if ("SUCCESS".equalsIgnoreCase(payment.getStatus())) {
            order.setStatus("PAID");
            orderRepository.save(order);
        }

        return paymentRepository.save(payment);
    }

    public Payment getPayment(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + id));
    }
}

