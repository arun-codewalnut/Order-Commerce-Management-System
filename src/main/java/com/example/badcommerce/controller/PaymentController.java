package com.example.badcommerce.controller;

import com.example.badcommerce.dto.PaymentRequestDto;
import com.example.badcommerce.entity.Payment;
import com.example.badcommerce.repository.PaymentRepository;
import com.example.badcommerce.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    /**
     * Anti-pattern: Non-idempotent payment API.
     * Missing Idempotency-Key header support; repeated client retries trigger duplicate payment charges.
     */
    @PostMapping
    public ResponseEntity<Payment> processPayment(@RequestBody PaymentRequestDto request) {
        Payment result = paymentService.processPayment(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public Payment getPayment(@PathVariable Long id) {
        return paymentService.getPayment(id);
    }

    @GetMapping
    public List<Payment> getPaymentsByOrder(@RequestParam(required = false) Long orderId) {
        if (orderId != null) {
            return paymentRepository.findByOrderId(orderId);
        }
        return paymentRepository.findAll();
    }
}

