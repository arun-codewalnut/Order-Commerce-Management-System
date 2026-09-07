package com.example.commerce.catalog.internal;

import com.example.commerce.catalog.internal.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {

    // Derived query
    Optional<Product> findBySku(String sku);

    // Derived query without indexing
    List<Product> findByCategory(String category);

    // Mixed query style: JPQL query returning entire entity
    @Query("SELECT p FROM Product p WHERE p.active = true AND p.price <= :maxPrice")
    List<Product> findAffordableProducts(double maxPrice);

    // Native query returning entire entity
    @Query(value = "SELECT * FROM products WHERE active = true ORDER BY created_at DESC", nativeQuery = true)
    List<Product> findRecentActiveProductsNative();
}
