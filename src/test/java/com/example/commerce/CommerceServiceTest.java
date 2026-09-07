package com.example.commerce;

import com.example.commerce.order.web.OrderRequestDto;
import com.example.commerce.customer.internal.Customer;
import com.example.commerce.catalog.internal.Product;
import com.example.commerce.inventory.internal.Inventory;
import com.example.commerce.order.internal.Order;
import com.example.commerce.order.internal.OrderItem;
import com.example.commerce.payment.internal.Payment;
import com.example.commerce.customer.internal.CustomerRepository;
import com.example.commerce.catalog.internal.ProductRepository;
import com.example.commerce.inventory.internal.InventoryRepository;
import com.example.commerce.order.internal.OrderRepository;
import com.example.commerce.order.internal.OrderItemRepository;
import com.example.commerce.payment.internal.PaymentRepository;
import com.example.commerce.notification.internal.AsyncNotificationProcessor;
import com.example.commerce.order.internal.CommerceService;
import com.example.commerce.payment.internal.ExternalPaymentClient;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommerceServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ExternalPaymentClient externalPaymentClient;
    @Mock private AsyncNotificationProcessor asyncNotificationProcessor;
    @Mock private CacheManager cacheManager;
    @Mock private Cache cache;
    @Mock private HttpServletRequest httpServletRequest;

    @InjectMocks
    private CommerceService commerceService;

    // Brittle Mockito test: mocking 10 internal collaborators and verifying each method call
    @Test
    public void testPlaceOrderWorkflow_Success() {
        Long customerId = 1L;
        Long productId = 10L;

        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setName("Alice");
        customer.setEmail("alice@example.com");
        customer.setActive(true);
        customer.setRole("CUSTOMER");

        Product product = new Product();
        product.setId(productId);
        product.setName("Widget");
        product.setPrice(100.0);
        product.setActive(true);

        Inventory inventory = new Inventory();
        inventory.setProductId(productId);
        inventory.setQuantity(10);
        inventory.setReservedQuantity(0);

        OrderRequestDto.OrderItemDto itemDto = new OrderRequestDto.OrderItemDto(productId, 2);
        OrderRequestDto dto = new OrderRequestDto(customerId, List.of(itemDto), "US");

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(externalPaymentClient.processPayment(any(), anyDouble(), anyString()))
                .thenReturn(Map.of("status", "SUCCESS", "reference", "TXN-123"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cacheManager.getCache("orders")).thenReturn(cache);

        ResponseEntity<Order> response = commerceService.placeOrderWorkflow(dto, httpServletRequest);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());

        // Brittle verification of internal interaction details
        verify(customerRepository).findById(customerId);
        verify(productRepository).findById(productId);
        verify(inventoryRepository, atLeastOnce()).findByProductId(productId);
        verify(externalPaymentClient).processPayment(any(), anyDouble(), eq("alice@example.com"));
        verify(asyncNotificationProcessor).sendOrderNotification(eq(customerId), any(), eq("alice@example.com"), anyString());
    }
}
