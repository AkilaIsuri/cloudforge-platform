package com.cloudforge.product_service.service;

import com.cloudforge.product_service.entity.Product;
import com.cloudforge.product_service.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product product(int stock) {
        Product product = new Product("Headphones", "desc", "Audio", new BigDecimal("100.00"), stock);
        product.setId(1L);
        return product;
    }

    @Test
    void reserveStock_takesStock_whenEnoughAvailable() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(10)));
        when(productRepository.decreaseStock(1L, 3)).thenReturn(1);

        productService.reserveStock(1L, 3);

        verify(productRepository).decreaseStock(1L, 3);
    }

    @Test
    void reserveStock_returnsConflict_whenNotEnoughStock() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(2)));
        when(productRepository.decreaseStock(1L, 5)).thenReturn(0);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> productService.reserveStock(1L, 5));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void getProduct_returnsNotFound_whenMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> productService.getProduct(99L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
