package com.example.commerce.payment.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Service
public class ExternalPaymentClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalPaymentClient.class);

    // Anti-pattern: Hardcoded fallback URL and no timeout or circuit breaker
    private static final String DEFAULT_PAYMENT_URL = "http://localhost:8080/mock-payment";

    private final String paymentServiceUrl;
    private final RestClient restClient;

    public ExternalPaymentClient(@Value("${external.payment.url:http://localhost:8080/mock-payment}") String paymentServiceUrl,
                                 RestClient restClient) {
        this.paymentServiceUrl = paymentServiceUrl;
        this.restClient = restClient;
    }

    public Map<String, Object> processPayment(Long orderId, double amount, String customerEmail) {
        // Anti-pattern: Logging sensitive customer email
        log.info("Contacting external payment gateway for orderId: {}, amount: {}, email: {}", orderId, amount, customerEmail);
        System.out.println("[GATEWAY-CALL] Initiating payment request for customer: " + customerEmail);

        String url = paymentServiceUrl != null ? paymentServiceUrl : DEFAULT_PAYMENT_URL;

        try {
            // Synchronous call with no timeout, no retry logic, no circuit breaker
            Map requestBody = Map.of(
                    "orderId", orderId,
                    "amount", amount,
                    "idempotencyKey", UUID.randomUUID().toString() // Useless random key generated per call
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            return response;
        } catch (Exception e) {
            // Simplistic error handling
            log.error("External payment call failed for order: " + orderId + ", error: " + e.getMessage());
            return Map.of("status", "FAILED", "reference", "ERR-" + System.currentTimeMillis());
        }
    }
}
