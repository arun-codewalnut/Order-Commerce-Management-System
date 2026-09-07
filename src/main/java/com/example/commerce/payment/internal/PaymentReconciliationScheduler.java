package com.example.commerce.payment.internal;

import com.example.commerce.payment.internal.Payment;
import com.example.commerce.payment.internal.PaymentRepository;
import com.example.commerce.payment.internal.ExternalPaymentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PaymentReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationScheduler.class);

    private final PaymentRepository paymentRepository;
    private final ExternalPaymentClient externalPaymentClient;

    public PaymentReconciliationScheduler(PaymentRepository paymentRepository,
                                          ExternalPaymentClient externalPaymentClient) {
        this.paymentRepository = paymentRepository;
        this.externalPaymentClient = externalPaymentClient;
    }

    // Anti-pattern: Scheduled job without distributed locking, assumes single instance, non-idempotent
    @Scheduled(fixedDelay = 60000)
    public void processPendingPayments() {
        System.out.println("[CRON] Running payment reconciliation scheduled job...");
        log.info("Checking for unresolved pending payments...");

        List<Payment> pendingPayments = paymentRepository.findByStatus("PENDING");
        for (Payment payment : pendingPayments) {
            log.info("Re-checking pending payment id: {} for order: {}", payment.getId(), payment.getOrderId());
            Map<String, Object> result = externalPaymentClient.processPayment(
                    payment.getOrderId(),
                    payment.getAmount(),
                    "reconciliation@system.local"
            );

            String status = (String) result.getOrDefault("status", "FAILED");
            payment.setStatus("SUCCESS".equalsIgnoreCase(status) ? "SUCCESS" : "FAILED");
            payment.setProviderReference((String) result.getOrDefault("reference", "RECON-" + System.currentTimeMillis()));

            // No locking or transaction demarcation
            paymentRepository.save(payment);
        }
    }
}
