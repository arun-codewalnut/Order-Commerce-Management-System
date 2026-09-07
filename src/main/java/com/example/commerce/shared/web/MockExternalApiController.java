package com.example.commerce.shared.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
public class MockExternalApiController {

    @PostMapping("/mock-payment")
    public ResponseEntity<Map<String, Object>> mockPaymentGateway(@RequestBody(required = false) Map<String, Object> body) {
        // Simulates remote payment gateway response
        String ref = "TXN-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "reference", ref,
                "timestamp", System.currentTimeMillis()
        ));
    }

    @PostMapping("/mock-notification")
    public ResponseEntity<Map<String, Object>> mockNotificationService(@RequestBody(required = false) Map<String, Object> body) {
        // Simulates remote notification delivery service
        return ResponseEntity.ok(Map.of(
                "status", "DELIVERED",
                "deliveryId", "NOTIF-" + UUID.randomUUID().toString().substring(0, 8)
        ));
    }
}
