package com.example.badcommerce.controller;

import com.example.badcommerce.entity.Customer;
import com.example.badcommerce.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    @Autowired // Anti-pattern: Field injection in controller
    private CustomerService customerService;

    // Anti-pattern: Exposing JPA entity directly as request and response body
    @PostMapping
    public ResponseEntity<Customer> createCustomer(@RequestBody Customer customer) {
        // Anti-pattern: Returning 200 OK instead of 201 CREATED
        Customer created = customerService.createCustomer(customer);
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{id}")
    public Customer getCustomer(@PathVariable Long id) {
        // Returns raw entity directly without DTO mapping or clean error wrapper
        return customerService.getCustomer(id);
    }

    // Anti-pattern: Unbounded collection return with findAll()
    @GetMapping
    public List<Customer> getAllCustomers() {
        return customerService.getAllCustomers();
    }

    // Anti-pattern: Mass assignment vulnerability - clients can bind role and active flags directly
    @PutMapping("/{id}")
    public Customer updateCustomer(@PathVariable Long id, @RequestBody Customer customer) {
        return customerService.updateCustomer(id, customer);
    }

    // Anti-pattern: Inconsistent status code - returning 200 with string body for DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.ok("Customer with ID " + id + " deleted successfully");
    }
}

