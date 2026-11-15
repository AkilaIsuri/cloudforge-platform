package com.cloudforge.product_service.config;

import com.cloudforge.product_service.entity.Product;
import com.cloudforge.product_service.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Adds the starter catalogue the first time the service starts on an empty database.
 * On an existing database it only fills in missing image URLs for the starter products.
 */
@Component
public class ProductSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;

    public ProductSeeder(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        List<Product> catalogue = catalogue();

        if (productRepository.count() == 0) {
            productRepository.saveAll(catalogue);
            return;
        }

        // Databases seeded before product images existed: add the image by product name.
        Map<String, String> imageByName = catalogue.stream()
                .collect(Collectors.toMap(Product::getName, Product::getImageUrl));
        List<Product> missingImages = productRepository.findAll().stream()
                .filter(p -> p.getImageUrl() == null && imageByName.containsKey(p.getName()))
                .toList();
        missingImages.forEach(p -> p.setImageUrl(imageByName.get(p.getName())));
        if (!missingImages.isEmpty()) {
            productRepository.saveAll(missingImages);
        }
    }

    private static List<Product> catalogue() {
        return List.of(
                product("Wireless Headphones", "Noise-cancelling over-ear headphones with 30-hour battery.", "Audio", "149.99", 50, "headphones"),
                product("Bluetooth Speaker", "Waterproof portable speaker with deep bass.", "Audio", "59.99", 60, "speaker"),
                product("Mechanical Keyboard", "Hot-swappable mechanical keyboard with RGB backlight.", "Accessories", "89.99", 40, "keyboard"),
                product("USB-C Hub", "7-in-1 hub with HDMI, USB 3.0 and card reader.", "Accessories", "39.99", 100, "usb-hub"),
                product("HD Webcam", "1080p webcam with dual microphones.", "Accessories", "69.99", 35, "webcam"),
                product("27\" 4K Monitor", "IPS display with USB-C power delivery.", "Displays", "349.00", 20, "monitor"),
                product("Laptop Pro 14", "14-inch laptop, 16 GB RAM, 512 GB SSD.", "Computers", "1299.00", 10, "laptop"),
                product("Smartwatch", "Fitness tracking, GPS and heart-rate monitor.", "Wearables", "199.00", 30, "smartwatch")
        );
    }

    private static Product product(String name, String description, String category,
                                   String price, int stock, String image) {
        Product product = new Product(name, description, category, new BigDecimal(price), stock);
        product.setImageUrl("/images/products/" + image + ".svg");
        return product;
    }
}
