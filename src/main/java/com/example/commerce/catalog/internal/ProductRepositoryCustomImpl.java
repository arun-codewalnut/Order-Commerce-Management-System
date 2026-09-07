package com.example.commerce.catalog.internal;

import com.example.commerce.catalog.internal.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProductRepositoryCustomImpl implements ProductRepositoryCustom {

    private final EntityManager entityManager;

    public ProductRepositoryCustomImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Product> searchProductsUnsafe(String keyword) {
        return entityManager.createQuery(
                        "SELECT p FROM Product p WHERE p.active = true AND LOWER(p.name) LIKE LOWER(:keyword)",
                        Product.class)
                .setParameter("keyword", "%" + keyword + "%")
                .getResultList();
    }
}
