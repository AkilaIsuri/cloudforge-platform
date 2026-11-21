package com.cloudforge.product_service.service;

import com.cloudforge.product_service.entity.Product;
import com.cloudforge.product_service.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getProducts(String category) {
        if (category == null || category.isBlank()) {
            return productRepository.findAll();
        }
        return productRepository.findByCategoryIgnoreCase(category);
    }

    public Product getProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> notFound(id));
    }

    public Product createProduct(Product product) {
        product.setId(null);
        return productRepository.save(product);
    }

    public Product updateProduct(Long id, Product updated) {
        Product existing = getProduct(id);
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setCategory(updated.getCategory());
        existing.setPrice(updated.getPrice());
        existing.setStock(updated.getStock());
        existing.setImageUrl(updated.getImageUrl());
        return productRepository.save(existing);
    }

    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw notFound(id);
        }
        productRepository.deleteById(id);
    }

    /** Called by order-service before payment. Fails with 409 if stock is too low. */
    @Transactional
    public Product reserveStock(Long id, int quantity) {
        Product product = getProduct(id);
        if (productRepository.decreaseStock(id, quantity) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Not enough stock for " + product.getName() + " (available: " + product.getStock() + ")");
        }
        return getProduct(id);
    }

    /** Called by order-service to give stock back when a payment fails. */
    @Transactional
    public Product releaseStock(Long id, int quantity) {
        getProduct(id);
        productRepository.increaseStock(id, quantity);
        return getProduct(id);
    }

    private ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Product " + id + " not found");
    }
}
