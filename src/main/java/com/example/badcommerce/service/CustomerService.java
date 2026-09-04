package com.example.badcommerce.service;

import com.example.badcommerce.entity.Customer;
import com.example.badcommerce.repository.CustomerRepository;
import com.example.badcommerce.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    @Autowired // Anti-pattern: Field injection
    private CustomerRepository customerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Customer createCustomer(Customer customer) {
        ValidationUtil.validateCustomer(customer);
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

