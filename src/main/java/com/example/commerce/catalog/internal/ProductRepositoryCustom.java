package com.example.commerce.catalog.internal;

import com.example.commerce.catalog.internal.Product;
import java.util.List;

public interface ProductRepositoryCustom {
    List<Product> searchProductsUnsafe(String keyword);
}
