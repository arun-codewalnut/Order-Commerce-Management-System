package com.example.badcommerce.repository;

import com.example.badcommerce.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByOrderId(Long orderId);
    List<Payment> findByCustomerId(Long customerId);
    List<Payment> findByStatus(String status);
}

