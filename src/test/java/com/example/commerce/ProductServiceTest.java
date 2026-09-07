package com.example.commerce;

import com.example.commerce.catalog.internal.Product;
import com.example.commerce.catalog.internal.ProductRepository;
import com.example.commerce.catalog.internal.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    // Anti-pattern: Brittle test verifying mock implementation details rather than business behavior
    @Test
    public void testCreateProduct_VerifiesInternalSaveCall() {
        Product p = new Product();
        p.setName("Test Product");
        p.setPrice(19.99);

        when(productRepository.save(any(Product.class))).thenReturn(p);

        Product result = productService.createProduct(p);

        assertNotNull(result);
        // Over-specified verification of implementation details
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    public void testGetProduct_HappyPathOnly() {
        Product p = new Product();
        p.setId(1L);
        p.setName("Keyboard");
        p.setPrice(50.0);

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        Product found = productService.getProduct(1L);

        assertNotNull(found);
        verify(productRepository).findById(1L);
    }
}
