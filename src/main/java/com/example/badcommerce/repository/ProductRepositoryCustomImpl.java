package com.example.badcommerce.repository;

import com.example.badcommerce.entity.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProductRepositoryCustomImpl implements ProductRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public List<Product> searchProductsUnsafe(String keyword) {
        // Intentional Anti-pattern: Constructing native SQL with string concatenation
        String sql = "SELECT * FROM products WHERE active = true AND name LIKE '%" + keyword + "%'";
        return entityManager.createNativeQuery(sql, Product.class).getResultList();
    }
}

