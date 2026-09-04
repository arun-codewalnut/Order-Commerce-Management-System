package com.example.badcommerce.repository;

import com.example.badcommerce.entity.Product;
import java.util.List;

public interface ProductRepositoryCustom {
    List<Product> searchProductsUnsafe(String keyword);
}

