package com.example.commerce.payment.web;

import com.example.commerce.payment.web.PaymentRequestDto;
import com.example.commerce.payment.internal.Payment;
import com.example.commerce.payment.internal.PaymentRepository;
import com.example.commerce.payment.internal.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;

    public PaymentController(PaymentService paymentService, PaymentRepository paymentRepository) {
        this.paymentService = paymentService;
        this.paymentRepository = paymentRepository;
    }

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
