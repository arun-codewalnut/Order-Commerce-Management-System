package com.example.commerce.notification.internal;

import com.example.commerce.notification.internal.Notification;
import com.example.commerce.notification.internal.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class AsyncNotificationProcessor {

    private static final Logger log = LoggerFactory.getLogger(AsyncNotificationProcessor.class);

    private final String notificationServiceUrl;
    private final NotificationRepository notificationRepository;
    private final RestClient restClient;

    public AsyncNotificationProcessor(
            @Value("${external.notification.url:http://localhost:8080/mock-notification}") String notificationServiceUrl,
            NotificationRepository notificationRepository, RestClient restClient) {
        this.notificationServiceUrl = notificationServiceUrl;
        this.notificationRepository = notificationRepository;
        this.restClient = restClient;
    }

    // Anti-pattern: In-process async work with unbounded executor, no deduplication / no outbox
    @Async
    public void sendOrderNotification(Long customerId, Long orderId, String customerEmail, String message) {
        System.out.println("[ASYNC-NOTIF] Sending notification to customer email: " + customerEmail);
        log.info("Sending async notification for orderId: {}, customerId: {}, email: {}", orderId, customerId, customerEmail);

        Notification notification = new Notification();
        notification.setCustomerId(customerId);
        notification.setOrderId(orderId);
        notification.setType("EMAIL");
        notification.setMessage(message);
        notification.setStatus("PENDING");
        notificationRepository.save(notification);

        try {
            // Synchronous call to mock notification endpoint inside async task
            restClient.post()
                    .uri(notificationServiceUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "customerId", customerId,
                            "email", customerEmail,
                            "message", message
                    ))
                    .retrieve()
                    .toBodilessEntity();

            notification.setStatus("SENT");
        } catch (Exception e) {
            log.error("Failed to send remote notification: {}", e.getMessage());
            notification.setStatus("FAILED");
        }

        // Duplicate side effect on every execution
        notificationRepository.save(notification);
    }
}
