package com.example.badcommerce.service;

import com.example.badcommerce.dto.OrderRequestDto;
import com.example.badcommerce.entity.*;
import com.example.badcommerce.repository.*;
import com.example.badcommerce.util.DateUtil;
import com.example.badcommerce.util.OrderUtil;
import com.example.badcommerce.util.PriceUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Map;

/**
 * Anti-pattern: "God Service" mixing business rules, entity mutations, database transactions,
 * external HTTP calls, HTTP controller concepts (ResponseEntity, HttpServletRequest), and caching.
 */
@Service
public class CommerceService {

    private static final Logger log = LoggerFactory.getLogger(CommerceService.class);

    // Excessive field injection with high cross-cutting coupling
    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ExternalPaymentClient externalPaymentClient;

    @Autowired
    private AsyncNotificationProcessor asyncNotificationProcessor;

    @Autowired
    private CacheManager cacheManager;

    /**
     * Anti-pattern: 100+ line transactional method that spans multiple domains, performs external
     * HTTP calls while holding a database transaction, mutates DB entities, calculates prices using double,
     * directly takes HttpServletRequest, and returns ResponseEntity.
     */
    @Transactional
    public ResponseEntity<Order> placeOrderWorkflow(OrderRequestDto request, HttpServletRequest httpRequest) {
        log.info("Starting monolithic placeOrderWorkflow for customerId: {}", request.getCustomerId());
        System.out.println("[COMMERCE-WORKFLOW] Processing order workflow requested via IP: " + httpRequest.getRemoteAddr());

        // 1. Customer Verification
        if (request.getCustomerId() == null) {
            log.error("Missing customerId in order request");
            return ResponseEntity.badRequest().build();
        }

        Customer customer = customerRepository.findById(request.getCustomerId()).orElse(null);
        if (customer == null) {
            System.out.println("Customer not found for id: " + request.getCustomerId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (!customer.isActive()) {
            log.warn("Customer account {} is inactive!", customer.getEmail());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Anti-pattern: Logging raw sensitive PII
        System.out.println("[PII-LOG] Order placed by customer: " + customer.getName() + " with email: " + customer.getEmail() + " and phone: " + customer.getPhone());

        // 2. Validate items
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // 3. Create initial order record
        Order order = new Order();
        order.setCustomerId(customer.getId());
        order.setStatus("PENDING");
        order.setCreatedAt(DateUtil.getCurrentTime());
        order.setUpdatedAt(DateUtil.getCurrentTime());
        order.setItems(new ArrayList<>());
        order = orderRepository.save(order);

        double subtotal = 0.0; // Anti-pattern: Using double for currency accumulator

        // 4. Validate inventory, reserve stock, calculate item totals
        for (OrderRequestDto.OrderItemDto itemDto : request.getItems()) {
            Product product = productRepository.findById(itemDto.getProductId()).orElse(null);
            if (product == null || !product.isActive()) {
                throw new IllegalArgumentException("Product unavailable: " + itemDto.getProductId());
            }

            Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElse(null);
            if (inventory == null || (inventory.getQuantity() - inventory.getReservedQuantity()) < itemDto.getQuantity()) {
                throw new IllegalStateException("Insufficient inventory for product: " + product.getName() + " (SKU: " + product.getSku() + ")");
            }

            // Reserve inventory directly
            inventory.setReservedQuantity(inventory.getReservedQuantity() + itemDto.getQuantity());
            inventoryRepository.save(inventory);

            // Calculate item price with double
            double itemTotal = PriceUtil.multiply(product.getPrice(), itemDto.getQuantity());
            subtotal += itemTotal;

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setOrderId(order.getId());
            orderItem.setProductId(product.getId());
            orderItem.setQuantity(itemDto.getQuantity());
            orderItem.setUnitPrice(product.getPrice());
            orderItem.setTotalPrice(itemTotal);

            orderItemRepository.save(orderItem);
            order.getItems().add(orderItem);
        }

        // 5. Apply business logic for discounts, shipping, and taxes
        double discountedSubtotal = OrderUtil.applyVipDiscount(subtotal, customer.getRole());
        String country = request.getShippingCountry() != null ? request.getShippingCountry() : "US";
        double shipping = OrderUtil.calculateShipping(discountedSubtotal, country);
        double tax = PriceUtil.addTax(discountedSubtotal, 0.08) - discountedSubtotal;
        double finalTotal = PriceUtil.roundToTwoDecimals(discountedSubtotal + shipping + tax);

        order.setTotalAmount(finalTotal);
        order.setStatus("CONFIRMED");
        order = orderRepository.save(order);

        // 6. External HTTP call while holding DB transaction
        log.info("Holding DB transaction while calling payment gateway for total: {}", finalTotal);
        Map<String, Object> paymentResponse = externalPaymentClient.processPayment(order.getId(), finalTotal, customer.getEmail());

        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setCustomerId(customer.getId());
        payment.setAmount(finalTotal);
        payment.setStatus("SUCCESS".equalsIgnoreCase((String) paymentResponse.get("status")) ? "SUCCESS" : "FAILED");
        payment.setProviderReference((String) paymentResponse.getOrDefault("reference", "UNSET"));
        paymentRepository.save(payment);

        if ("SUCCESS".equalsIgnoreCase(payment.getStatus())) {
            order.setStatus("PAID");
            order = orderRepository.save(order);

            // Deduct from actual inventory quantity now that order is paid
            for (OrderItem item : order.getItems()) {
                Inventory inv = inventoryRepository.findByProductId(item.getProductId()).orElse(null);
                if (inv != null) {
                    inv.setQuantity(inv.getQuantity() - item.getQuantity());
                    inv.setReservedQuantity(inv.getReservedQuantity() - item.getQuantity());
                    inventoryRepository.save(inv);
                }
            }
        }

        // 7. Directly trigger notification without Transactional Outbox
        asyncNotificationProcessor.sendOrderNotification(
                customer.getId(),
                order.getId(),
                customer.getEmail(),
                "Your order #" + order.getId() + " has been processed with status: " + order.getStatus()
        );

        // 8. Direct cache manipulation
        if (cacheManager.getCache("orders") != null) {
            cacheManager.getCache("orders").put(order.getId(), order);
        }

        log.info("Finished placeOrderWorkflow successfully for orderId: {}", order.getId());
        return ResponseEntity.ok(order);
    }
}

