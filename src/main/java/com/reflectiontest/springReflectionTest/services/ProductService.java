package com.reflectiontest.springReflectionTest.services;

import com.reflectiontest.springReflectionTest.annotations.ExpectedResult;
import com.reflectiontest.springReflectionTest.annotations.IntegrationTest;
import com.reflectiontest.springReflectionTest.models.Product;
import com.reflectiontest.springReflectionTest.repositories.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProductService {
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "{\"name\": \"Laptop\", \"price\": 1200.00}", expectedJson = "{\"name\": \"Laptop\", \"price\": 1200.00}")
    @ExpectedResult(inputJson = "{\"name\": \"Mouse\", \"price\": 25.00}", expectedJson = "{\"name\": \"Mouse\", \"price\": 25.00}")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "__NULL__")
    @ExpectedResult(inputJson = "{\"name\": null, \"price\": 50.00}", expectedJson = "__NULL__")
    public Product getProductDetails(Product product) {
        if (product == null || product.getName() == null) return null;

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
        if (product == null) return false;
        productRepository.save(product);
        return isProductCached(product);
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "100.00", expectedJson = "[\"Laptop\", \"Mouse\"]")
    @ExpectedResult(inputJson = "50.00", expectedJson = "[\"Mouse\"]")
    @ExpectedResult(inputJson = "1000.00", expectedJson = "[\"Laptop\"]")
    public List<String> findProductsCheaperThan(double price) {
        return productRepository.findByPriceLessThan(price)
                .stream()
                .map(Product::getName)
                .collect(Collectors.toList());
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "\"key\"", expectedJson = "[\"Keyboard\", \"Headphones\"]")
    @ExpectedResult(inputJson = "\"top\"", expectedJson = "[\"Laptop\"]")
    @ExpectedResult(inputJson = "\"non-existent\"", expectedJson = "[]")
    public List<String> findProductsByNamePattern(String pattern) {
        return productRepository.findProductsByNamePattern(".*" + pattern + ".*")
                .stream()
                .map(Product::getName)
                .collect(Collectors.toList());
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "{\"name\": \"DeleteTest\", \"price\": 99.99}", expectedJson = "false")
    public boolean deleteProductAndVerify(Product product) {
        if (product == null || product.getName() == null) return false;

        productRepository.save(product);
        productRepository.deleteByName(product.getName());

        return !productRepository.existsByName(product.getName());
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "{\"name\": \"RangeTest\", \"price\": 75.00}", expectedJson = "true")
    @ExpectedResult(inputJson = "{\"name\": \"OutOfRangeTest\", \"price\": 200.00}", expectedJson = "false")
    public boolean isProductInPriceRange(Product product) {
        if (product == null) return false;

        productRepository.save(product);

        List<Product> productsInRange = productRepository.findByPriceBetween(50.0, 100.0);
        return productsInRange.stream().anyMatch(p -> p.getName().equals(product.getName()));
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "3", expectedJson = "3")
    @ExpectedResult(inputJson = "1", expectedJson = "1")
    @ExpectedResult(inputJson = "0", expectedJson = "0")
    @ExpectedResult(inputJson = "10", expectedJson = "5")
    public int countTotalProducts(int limit) {
        List<Product> products = productRepository.findAll();
        return Math.min(products.size(), limit);
    }
}