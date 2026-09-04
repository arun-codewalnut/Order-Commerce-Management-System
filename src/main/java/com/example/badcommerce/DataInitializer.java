package com.example.badcommerce;

import com.example.badcommerce.entity.*;
import com.example.badcommerce.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

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
    private AddressRepository addressRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (customerRepository.count() > 0) {
            log.info("Database already seeded. Skipping initial data loading.");
            return;
        }

        log.info("Seeding sample data for bad-commerce-api...");

        // 1. Customers
        Customer alice = new Customer();
        alice.setName("Alice Smith");
        alice.setEmail("alice@example.com");
        alice.setPhone("+1-555-0101");
        alice.setPassword(passwordEncoder.encode("password123"));
        alice.setRole("CUSTOMER");
        alice.setActive(true);
        alice = customerRepository.save(alice);

        Customer bob = new Customer();
        bob.setName("Bob Jones");
        bob.setEmail("bob@example.com");
        bob.setPhone("+1-555-0102");
        bob.setPassword(passwordEncoder.encode("password123"));
        bob.setRole("CUSTOMER");
        bob.setActive(true);
        bob = customerRepository.save(bob);

        Customer admin = new Customer();
        admin.setName("Charlie Admin");
        admin.setEmail("admin@example.com");
        admin.setPhone("+1-555-0199");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setRole("ADMIN");
        admin.setActive(true);
        admin = customerRepository.save(admin);

        // 2. Addresses
        Address address = new Address();
        address.setCustomerId(alice.getId());
        address.setStreet("123 Commerce Way");
        address.setCity("Seattle");
        address.setState("WA");
        address.setZipCode("98101");
        address.setCountry("US");
        addressRepository.save(address);

        // 3. Products
        Product p1 = new Product();
        p1.setName("Wireless Noise-Canceling Headphones");
        p1.setDescription("Premium over-ear headphones with active noise cancellation");
        p1.setPrice(199.99);
        p1.setSku("TECH-WNC-01");
        p1.setCategory("Electronics");
        p1.setActive(true);
        p1 = productRepository.save(p1);

        Product p2 = new Product();
        p2.setName("Mechanical Gaming Keyboard");
        p2.setDescription("RGB backlit mechanical keyboard with blue switches");
        p2.setPrice(89.50);
        p2.setSku("TECH-KB-02");
        p2.setCategory("Electronics");
        p2.setActive(true);
        p2 = productRepository.save(p2);

        Product p3 = new Product();
        p3.setName("Ergonomic Office Chair");
        p3.setDescription("Adjustable lumbar support ergonomic mesh chair");
        p3.setPrice(249.00);
        p3.setSku("FURN-CHAIR-03");
        p3.setCategory("Furniture");
        p3.setActive(true);
        p3 = productRepository.save(p3);

        Product p4 = new Product();
        p4.setName("Stainless Steel Insulated Water Bottle");
        p4.setDescription("Vacuum-insulated 32oz bottle keeps drinks cold 24h");
        p4.setPrice(24.95);
        p4.setSku("HOME-BOTTLE-04");
        p4.setCategory("Home");
        p4.setActive(true);
        p4 = productRepository.save(p4);

        Product p5 = new Product();
        p5.setName("Organic Fair-Trade Coffee Beans");
        p5.setDescription("Dark roast whole bean coffee 1kg bag");
        p5.setPrice(15.75);
        p5.setSku("FOOD-COFFEE-05");
        p5.setCategory("Grocery");
        p5.setActive(true);
        p5 = productRepository.save(p5);

        // 4. Inventory
        createInventory(p1.getId(), 50, 2);
        createInventory(p2.getId(), 100, 5);
        createInventory(p3.getId(), 20, 1);
        createInventory(p4.getId(), 200, 0);
        createInventory(p5.getId(), 150, 10);

        // 5. Orders & Items
        Order order1 = new Order();
        order1.setCustomerId(alice.getId());
        order1.setStatus("PAID");
        order1.setTotalAmount(249.89);
        order1.setCreatedAt(LocalDateTime.now().minusDays(2));
        order1.setUpdatedAt(LocalDateTime.now().minusDays(2));
        order1.setItems(new ArrayList<>());
        order1 = orderRepository.save(order1);

        OrderItem item1 = new OrderItem();
        item1.setOrder(order1);
        item1.setOrderId(order1.getId());
        item1.setProductId(p1.getId());
        item1.setQuantity(1);
        item1.setUnitPrice(p1.getPrice());
        item1.setTotalPrice(p1.getPrice());
        orderItemRepository.save(item1);
        order1.getItems().add(item1);

        OrderItem item2 = new OrderItem();
        item2.setOrder(order1);
        item2.setOrderId(order1.getId());
        item2.setProductId(p4.getId());
        item2.setQuantity(2);
        item2.setUnitPrice(p4.getPrice());
        item2.setTotalPrice(49.90);
        orderItemRepository.save(item2);
        order1.getItems().add(item2);

        Order order2 = new Order();
        order2.setCustomerId(bob.getId());
        order2.setStatus("PENDING");
        order2.setTotalAmount(89.50);
        order2.setCreatedAt(LocalDateTime.now().minusHours(4));
        order2.setUpdatedAt(LocalDateTime.now().minusHours(4));
        order2.setItems(new ArrayList<>());
        order2 = orderRepository.save(order2);

        OrderItem item3 = new OrderItem();
        item3.setOrder(order2);
        item3.setOrderId(order2.getId());
        item3.setProductId(p2.getId());
        item3.setQuantity(1);
        item3.setUnitPrice(p2.getPrice());
        item3.setTotalPrice(p2.getPrice());
        orderItemRepository.save(item3);
        order2.getItems().add(item3);

        // 6. Payments
        Payment payment1 = new Payment();
        payment1.setOrderId(order1.getId());
        payment1.setCustomerId(alice.getId());
        payment1.setAmount(249.89);
        payment1.setStatus("SUCCESS");
        payment1.setProviderReference("SEED-TXN-1001");
        paymentRepository.save(payment1);

        Payment payment2 = new Payment();
        payment2.setOrderId(order2.getId());
        payment2.setCustomerId(bob.getId());
        payment2.setAmount(89.50);
        payment2.setStatus("PENDING");
        payment2.setProviderReference("SEED-TXN-1002");
        paymentRepository.save(payment2);

        // 7. Notifications
        Notification notif = new Notification();
        notif.setCustomerId(alice.getId());
        notif.setOrderId(order1.getId());
        notif.setType("EMAIL");
        notif.setMessage("Order #1 has been confirmed and paid.");
        notif.setStatus("SENT");
        notificationRepository.save(notif);

        log.info("Sample data initialization completed successfully.");
    }

    private void createInventory(Long productId, int quantity, int reserved) {
        Inventory inv = new Inventory();
        inv.setProductId(productId);
        inv.setQuantity(quantity);
        inv.setReservedQuantity(reserved);
        inventoryRepository.save(inv);
    }
}

