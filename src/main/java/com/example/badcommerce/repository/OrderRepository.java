package com.example.badcommerce.repository;

import com.example.badcommerce.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Poor indexing target
    List<Order> findByCustomerId(Long customerId);

    List<Order> findByStatus(String status);

    List<Order> findByCustomerIdAndStatus(Long customerId, String status);

    @Query("SELECT o FROM Order o WHERE o.totalAmount >= :minAmount")
    List<Order> findHighValueOrders(double minAmount);
}

