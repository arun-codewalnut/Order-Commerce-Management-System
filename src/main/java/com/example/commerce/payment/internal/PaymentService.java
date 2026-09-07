package com.example.commerce.payment.internal;

import com.example.commerce.payment.web.PaymentRequestDto;
import com.example.commerce.customer.internal.Customer;
import com.example.commerce.order.internal.Order;
import com.example.commerce.payment.internal.Payment;
import com.example.commerce.customer.internal.CustomerRepository;
import com.example.commerce.order.internal.OrderRepository;
import com.example.commerce.payment.internal.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ExternalPaymentClient externalPaymentClient;

    public PaymentService(PaymentRepository paymentRepository, OrderRepository orderRepository,
                          CustomerRepository customerRepository, ExternalPaymentClient externalPaymentClient) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.externalPaymentClient = externalPaymentClient;
    }

    // Anti-pattern: Non-idempotent payment processing. Repeating requests create duplicate charges
    public Payment processPayment(PaymentRequestDto request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + request.orderId()));

        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + request.customerId()));

        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setCustomerId(customer.getId());
        payment.setAmount(request.amount());
        payment.setStatus("PENDING");
        payment = paymentRepository.save(payment);

        // Call external gateway directly
        Map<String, Object> gatewayResponse = externalPaymentClient.processPayment(
                order.getId(),
                request.amount(),
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
