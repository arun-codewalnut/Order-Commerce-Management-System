package com.example.badcommerce.controller;

import com.example.badcommerce.entity.Notification;
import com.example.badcommerce.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

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

