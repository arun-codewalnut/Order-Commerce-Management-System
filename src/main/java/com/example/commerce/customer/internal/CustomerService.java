package com.example.commerce.customer.internal;

import com.example.commerce.customer.internal.Customer;
import com.example.commerce.customer.internal.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomerService(CustomerRepository customerRepository, PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Customer createCustomer(Customer customer) {
        CustomerValidator.validate(customer);
        if (customerRepository.findByEmail(customer.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Customer with email already exists: " + customer.getEmail());
        }
        if (customer.getPassword() != null) {
            customer.setPassword(passwordEncoder.encode(customer.getPassword()));
        }
        customer.setActive(true);
        if (customer.getRole() == null) {
            customer.setRole("CUSTOMER");
        }
        log.info("Creating customer with email: {}", customer.getEmail());
        return customerRepository.save(customer);
    }

    public Customer getCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + id));
    }

    public List<Customer> getAllCustomers() {
        // Anti-pattern: unbounded findAll()
        return customerRepository.findAll();
    }

    public Customer updateCustomer(Long id, Customer customerDetails) {
        Customer customer = getCustomer(id);

        // Anti-pattern: Mass assignment vulnerability - allowing client to change role, active status, etc.
        customer.setName(customerDetails.getName());
        customer.setEmail(customerDetails.getEmail());
        customer.setPhone(customerDetails.getPhone());
        customer.setRole(customerDetails.getRole()); // Flaw: Role escalation
        customer.setActive(customerDetails.isActive());

        return customerRepository.save(customer);
    }

    public void deleteCustomer(Long id) {
        Customer customer = getCustomer(id);
        customerRepository.delete(customer);
    }
}
