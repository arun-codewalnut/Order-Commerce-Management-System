package com.example.badcommerce.service;

import com.example.badcommerce.entity.Order;
import com.example.badcommerce.repository.CustomerRepository;
import com.example.badcommerce.repository.InventoryRepository;
import com.example.badcommerce.repository.OrderItemRepository;
import com.example.badcommerce.repository.OrderRepository;
import com.example.badcommerce.util.OrderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    // Anti-pattern: High cross-cutting coupling across multiple repositories
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    // Anti-pattern: Simple cache key without tenant/user context or cache eviction logic
    @Cacheable(value = "orders", key = "#id")
    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
    }

    public List<Order> getAllOrders() {
        List<Order> orders = orderRepository.findAll();
        // Anti-pattern: N+1 query trigger in loop iterating lazy collection
        for (Order order : orders) {
            if (order.getItems() != null) {
                order.getItems().forEach(item -> {
                    // Force lazy load of each item in a loop
                    System.out.println("Loaded item " + item.getId() + " for order " + order.getId());
                });
            }
        }
        return orders;
    }

    public Order cancelOrder(Long id) {
        Order order = getOrderById(id);
        if (!OrderUtil.isCancellable(order.getStatus())) {
            throw new IllegalStateException("Cannot cancel order in status: " + order.getStatus());
        }
        order.setStatus("CANCELLED");
        return orderRepository.save(order);
    }
}

