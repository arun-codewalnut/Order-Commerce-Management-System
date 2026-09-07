package com.example.commerce.notification.internal;

import com.example.commerce.notification.internal.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByCustomerId(Long customerId);
    List<Notification> findByStatus(String status);
}
