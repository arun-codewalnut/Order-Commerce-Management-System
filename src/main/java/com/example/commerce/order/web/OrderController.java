package com.example.commerce.order.web;

import com.example.commerce.order.web.OrderRequestDto;
import com.example.commerce.customer.internal.Customer;
import com.example.commerce.order.internal.Order;
import com.example.commerce.customer.internal.CustomerRepository;
import com.example.commerce.order.internal.OrderRepository;
import com.example.commerce.order.internal.CommerceService;
import com.example.commerce.order.internal.OrderService;
import com.example.commerce.shared.support.SecurityUtil;
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
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final CommerceService commerceService;
    private final OrderService orderService;

    public OrderController(OrderRepository orderRepository, CustomerRepository customerRepository,
                           CommerceService commerceService, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.commerceService = commerceService;
        this.orderService = orderService;
    }

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
    public OrderResponse getOrder(
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

        return OrderResponse.from(order);
    }

    // Anti-pattern: Triggers N+1 query problem by iterating and accessing lazy items
    @GetMapping
    public List<OrderResponse> getAllOrders() {
        return orderService.getAllOrders().stream().map(OrderResponse::from).toList();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Order> cancelOrder(@PathVariable Long id) {
        Order cancelled = orderService.cancelOrder(id);
        return ResponseEntity.ok(cancelled);
    }
}
