package com.example.commerce.notification.web;

import com.example.commerce.notification.internal.Notification;
import com.example.commerce.notification.internal.NotificationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public List<Notification> getNotifications(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status) {
        if (customerId != null) {
            return notificationRepository.findByCustomerId(customerId);
        }
        if (status != null) {
            return notificationRepository.findByStatus(status);
        }
        return notificationRepository.findAll();
    }
}
