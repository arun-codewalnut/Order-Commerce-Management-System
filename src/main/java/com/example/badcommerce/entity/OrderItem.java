package com.example.badcommerce.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "order_items")
@Data
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonIgnore // Prevent infinite JSON recursion while preserving lazy loading / Lombok toString circularity
    private Order order;

    // Redundant orderId column / primitive FK
    @Column(name = "order_id", insertable = false, updatable = false)
    private Long orderId;

    private Long productId;

    private int quantity;

    private double unitPrice;

    private double totalPrice;
}

