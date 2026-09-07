package com.example.commerce.catalog.internal;

import com.example.commerce.catalog.internal.Product;
import com.example.commerce.catalog.internal.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product createProduct(Product product) {
        ProductValidator.validate(product);
        product.setActive(true);
        return productRepository.save(product);
    }

    public Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
    }

    public List<Product> getAllProducts(String sortBy) {
        // Anti-pattern: Unvalidated sort parameter passed directly from client
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            return productRepository.findAll(Sort.by(sortBy));
        }
        return productRepository.findAll();
    }

    public List<Product> searchProducts(String keyword) {
        // Calls repository with native SQL query constructed by string concatenation
        return productRepository.searchProductsUnsafe(keyword);
    }

    public Product updateProduct(Long id, Product details) {
        Product product = getProduct(id);
        product.setName(details.getName());
        product.setDescription(details.getDescription());
        product.setPrice(details.getPrice());
        product.setCategory(details.getCategory());
        product.setActive(details.isActive());
        return productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        Product product = getProduct(id);
        productRepository.delete(product);
    }
}
