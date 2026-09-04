package com.example.badcommerce;

import com.example.badcommerce.entity.Product;
import com.example.badcommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    public void testSearchProductsUnsafe_ReturnsMatchingProducts() {
        // Test execution of custom native SQL search query
        List<Product> results = productRepository.searchProductsUnsafe("Keyboard");
        assertNotNull(results);
        assertFalse(results.isEmpty());
    }

    @Test
    public void testFindAffordableProducts_JPQL() {
        List<Product> products = productRepository.findAffordableProducts(100.0);
        assertNotNull(products);
    }
}

