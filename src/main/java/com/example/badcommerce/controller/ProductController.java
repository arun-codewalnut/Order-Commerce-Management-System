package com.example.badcommerce.controller;

import com.example.badcommerce.dto.GenericResponse;
import com.example.badcommerce.entity.Product;
import com.example.badcommerce.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    // Inconsistent API response: wraps product in GenericResponse whereas CustomerController returned raw entity
    @PostMapping
    public ResponseEntity<GenericResponse<Product>> createProduct(@RequestBody Product product) {
        Product created = productService.createProduct(product);
        return ResponseEntity.ok(GenericResponse.of(created, "Product created successfully"));
    }

    @GetMapping("/{id}")
    public Product getProduct(@PathVariable Long id) {
        return productService.getProduct(id);
    }

    // Anti-pattern: Unvalidated sort parameter and unbounded collection
    @GetMapping
    public List<Product> getAllProducts(@RequestParam(required = false) String sortBy) {
        return productService.getAllProducts(sortBy);
    }

    // Anti-pattern: Exposes unsafe native SQL query via endpoint
    @GetMapping("/search")
    public List<Product> searchProducts(@RequestParam String keyword) {
        return productService.searchProducts(keyword);
    }

    @PutMapping("/{id}")
    public Product updateProduct(@PathVariable Long id, @RequestBody Product product) {
        return productService.updateProduct(id, product);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok().build(); // Inconsistent: 200 OK without body vs CustomerController's string message
    }
}

