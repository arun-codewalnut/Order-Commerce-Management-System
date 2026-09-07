package com.example.commerce.order.internal;

import com.example.commerce.order.internal.Order;
import com.example.commerce.customer.internal.CustomerRepository;
import com.example.commerce.inventory.internal.InventoryRepository;
import com.example.commerce.order.internal.OrderItemRepository;
import com.example.commerce.order.internal.OrderRepository;
import com.example.commerce.shared.support.OrderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    // Anti-pattern: High cross-cutting coupling across multiple repositories
    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

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
