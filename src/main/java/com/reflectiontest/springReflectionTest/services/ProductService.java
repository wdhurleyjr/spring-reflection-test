package com.reflectiontest.springReflectionTest.services;

import com.reflectiontest.springReflectionTest.annotations.ExpectedResult;
import com.reflectiontest.springReflectionTest.annotations.IntegrationTest;
import com.reflectiontest.springReflectionTest.annotations.MockDependency;
import com.reflectiontest.springReflectionTest.models.Product;
import com.reflectiontest.springReflectionTest.repositories.ProductRepository;
import com.reflectiontest.springReflectionTest.repositories.SearchHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Product service that demonstrates various testing scenarios
 * including complex objects, collections, exceptions, and edge cases.
 *
 * Implementation is stateless - relies on injected repositories for all data access.
 */
@Service
public class ProductService {

    @MockDependency
    private ProductRepository productRepository;

    @MockDependency
    private SearchHistoryRepository searchRepository;

    public ProductService() {
        // Default no-arg constructor
    }

    public ProductService(ProductRepository productRepository, SearchHistoryRepository searchRepository) {
        this.productRepository = productRepository;
        this.searchRepository = searchRepository;
    }

    /**
     * Adds a product to the repository if it doesn't already exist
     */
    public void addProduct(Product product) {
        if (product == null || product.getName() == null) {
            return;
        }
        productRepository.save(product);
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "{\"name\": \"Laptop\", \"price\": 1200.00}", expectedJson = "{\"name\": \"Laptop\", \"price\": 1200.00}")
    @ExpectedResult(inputJson = "{\"name\": \"Mouse\", \"price\": 25.00}", expectedJson = "{\"name\": \"Mouse\", \"price\": 25.00}")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "__NULL__")
    @ExpectedResult(inputJson = "{\"name\": null, \"price\": 50.00}", expectedJson = "__NULL__")
    public Product getProductDetails(Product product) {
        if (product == null || product.getName() == null) return null;

        // Record search history
        searchRepository.saveSearch(product.getName());

        // Try to find product in our repository
        return productRepository.findByName(product.getName()).orElse(null);
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "{\"name\": \"Keyboard\", \"price\": 50.00}", expectedJson = "false")
    @ExpectedResult(inputJson = "{\"name\": \"Monitor\", \"price\": 300.00}", expectedJson = "false")
    @ExpectedResult(inputJson = "{\"name\": \"Mouse\", \"price\": 25.00}", expectedJson = "true")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "false")
    public boolean isProductCached(Product product) {
        if (product == null || product.getName() == null) {
            return false;
        }
        return productRepository.existsByName(product.getName());
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "{\"name\": \"Headphones\", \"price\": 150.00}", expectedJson = "true")
    @ExpectedResult(inputJson = "{\"name\": \"Webcam\", \"price\": 75.00}", expectedJson = "true")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "false")
    public boolean addAndCheckCache(Product product) {
        addProduct(product);
        return product != null && isProductCached(product);
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "{\"name\": \"Tablet\", \"price\": 300.00}", expectedJson = "true")
    @ExpectedResult(inputJson = "{\"name\": \"Smartphone\", \"price\": 800.00}", expectedJson = "true")
    public boolean addProductTwiceAndCheck(Product product) {
        addProduct(product);
        addProduct(product); // Second call should be idempotent

        if (product == null || product.getName() == null) {
            return false;
        }

        return productRepository.existsByName(product.getName());
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "3", expectedJson = "[\"Laptop\", \"Mouse\", \"Keyboard\"]")
    @ExpectedResult(inputJson = "1", expectedJson = "[\"Laptop\"]")
    @ExpectedResult(inputJson = "0", expectedJson = "[]")
    @ExpectedResult(inputJson = "10", expectedJson = "[\"Laptop\", \"Mouse\", \"Keyboard\", \"Headphones\", \"Monitor\"]")
    public List<String> getTopSearchedProducts(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }

        // Get search history with counts from repository
        Map<String, Long> counts = searchRepository.getSearchCounts();

        // Sort by count (descending) and return top results
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }
}
