package com.example.commerce.order.internal;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    // Redundant orderId column / primitive FK
    @Column(name = "order_id", insertable = false, updatable = false)
    private Long orderId;

    private Long productId;

    private int quantity;

    private double unitPrice;

    private double totalPrice;
}
