package com.example.badcommerce.controller;

import com.example.badcommerce.dto.OrderRequestDto;
import com.example.badcommerce.entity.Customer;
import com.example.badcommerce.entity.Order;
import com.example.badcommerce.repository.CustomerRepository;
import com.example.badcommerce.repository.OrderRepository;
import com.example.badcommerce.service.CommerceService;
import com.example.badcommerce.service.OrderService;
import com.example.badcommerce.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    // Anti-pattern: Controller directly injects multiple repositories alongside services
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CommerceService commerceService;

    @Autowired
    private OrderService orderService;

    // Anti-pattern: Direct delegation of HTTP request/response to god service
    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody OrderRequestDto request, HttpServletRequest httpRequest) {
        return commerceService.placeOrderWorkflow(request, httpRequest);
    }

    /**
     * Anti-pattern: Realistic IDOR vulnerability.
     * The endpoint trusts the client-supplied `customerId` query parameter or header to determine authorization,
     * rather than verifying the authenticated principal from Spring Security's SecurityContext.
     */
    @GetMapping("/{id}")
    @Cacheable(value = "orders", key = "#id") // Simplistic cache key without actor isolation or eviction strategy
    public Order getOrder(
            @PathVariable Long id,
            @RequestParam(required = false) Long customerId,
            HttpServletRequest request) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with id: " + id));

        // Insecure authorization logic: trusting customerId parameter from query or header
        Long resolvedCustomerId = SecurityUtil.resolveCustomerId(request, customerId);
        if (resolvedCustomerId != null) {
            Customer customer = customerRepository.findById(resolvedCustomerId).orElse(null);
            String role = customer != null ? customer.getRole() : "CUSTOMER";
            if (!SecurityUtil.isSelfOrAdmin(order.getCustomerId(), resolvedCustomerId, role)) {
                // If ID matches, access is granted even if the logged-in user is someone else!
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to order " + id);
            }
        }

        return order;
    }

    // Anti-pattern: Triggers N+1 query problem by iterating and accessing lazy items
    @GetMapping
    public List<Order> getAllOrders() {
        return orderService.getAllOrders();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Order> cancelOrder(@PathVariable Long id) {
        Order cancelled = orderService.cancelOrder(id);
        return ResponseEntity.ok(cancelled);
    }
}

