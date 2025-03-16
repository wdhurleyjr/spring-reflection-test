package com.reflectiontest.springReflectionTest.examples;

import com.reflectiontest.springReflectionTest.annotations.ExpectedResult;
import com.reflectiontest.springReflectionTest.annotations.MockDependency;
import com.reflectiontest.springReflectionTest.repositories.ExternalProductRepository;
import org.springframework.stereotype.Service;
import com.reflectiontest.springReflectionTest.models.Product;

import java.util.HashMap;
import java.util.Map;

@Service
public class ProductService {

    @MockDependency
    private ExternalProductRepository productRepository;

    private final Map<String, Product> productCache = new HashMap<>();

    // Accessory method
    public void addProduct(Product product) {
        if (product == null || product.getName() == null) {
            return;
        }
        if (!productRepository.existsByName(product.getName())) {
            productCache.putIfAbsent(product.getName(), product);
        }
    }

    @ExpectedResult(inputJson = "{\"name\": \"Laptop\", \"price\": 1200.00}", expectedJson = "{\"name\": \"Laptop\", \"price\": 1200.00}")
    @ExpectedResult(inputJson = "{\"name\": \"Mouse\", \"price\": 25.00}", expectedJson = "{\"name\": \"Mouse\", \"price\": 25.00}")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "__NULL__")
    public Product getProductDetails(Product product) {
        if (product == null) return null;

        if (!productCache.containsKey(product.getName())) {
            if (productRepository.existsByName(product.getName())) {
                return product;
            }
            addProduct(product);
        }

        return productCache.get(product.getName());
    }

    @ExpectedResult(inputJson = "{\"name\": \"Keyboard\", \"price\": 50.00}", expectedJson = "false")
    @ExpectedResult(inputJson = "{\"name\": \"Monitor\", \"price\": 300.00}", expectedJson = "false")
    @ExpectedResult(inputJson = "{\"name\": \"Mouse\", \"price\": 25.00}", expectedJson = "true")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "false")
    public boolean isProductCached(Product product) {
        return product != null && (productCache.containsKey(product.getName()) || productRepository.existsByName(product.getName()));
    }

    @ExpectedResult(inputJson = "{\"name\": \"Headphones\", \"price\": 150.00}", expectedJson = "true")
    @ExpectedResult(inputJson = "{\"name\": \"Webcam\", \"price\": 75.00}", expectedJson = "true")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "false")
    public boolean addAndCheckCache(Product product) {
        addProduct(product);
        return product != null && (productCache.containsKey(product.getName()) || productRepository.existsByName(product.getName()));
    }

    @ExpectedResult(inputJson = "{\"name\": \"Tablet\", \"price\": 300.00}", expectedJson = "true")
    @ExpectedResult(inputJson = "{\"name\": \"Smartphone\", \"price\": 800.00}", expectedJson = "true")
    public boolean addProductTwiceAndCheck(Product product) {
        addProduct(product);
        addProduct(product);
        return productCache.containsKey(product.getName());
    }

    @ExpectedResult(inputJson = "[5, 3]", expectedJson = "8")
    @ExpectedResult(inputJson = "[-2, 7]", expectedJson = "5")
    @ExpectedResult(inputJson = "[0, 0]", expectedJson = "0")
    public int addTwoNumbers(int a, int b) {
        return a + b;
    }

    @ExpectedResult(inputJson = "5", expectedJson = "50")
    @ExpectedResult(inputJson = "-3", expectedJson = "-30")
    @ExpectedResult(inputJson = "0", expectedJson = "0")
    @ExpectedResult(inputJson = "10", expectedJson = "100")
    @ExpectedResult(inputJson = "1", expectedJson = "10")
    public int multiplyByTen(int a) {
        return a * 10;
    }

    @ExpectedResult(inputJson = "[10, 5]", expectedJson = "5")
    @ExpectedResult(inputJson = "[3, 10]", expectedJson = "-7")
    @ExpectedResult(inputJson = "[0, 5]", expectedJson = "-5")
    public int subtractTwoNumbers(int a, int b) {
        return a - b;
    }

    @ExpectedResult(inputJson = "[10.0, 2.0]", expectedJson = "5.0")
    @ExpectedResult(inputJson = "[-100.0, 10.0]", expectedJson = "-10.0")
    @ExpectedResult(inputJson = "[0.0, 5.0]", expectedJson = "0.0")
    @ExpectedResult(inputJson = "[10.0, 0.0]", expectedJson = "__THROWS__")
    public double divideTwoNumbers(double a, double b) {
        if (b == 0) {
            throw new ArithmeticException("Cannot divide by zero");
        }
        return a / b;
    }

    @ExpectedResult(inputJson = "\"hello\"", expectedJson = "\"olleh\"")
    @ExpectedResult(inputJson = "\"Test\"", expectedJson = "\"tseT\"")
    @ExpectedResult(inputJson = "\"\"", expectedJson = "\"\"")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "__NULL__")
    public String reverseString(String input) {
        if (input == null) return null;
        return new StringBuilder(input).reverse().toString();
    }

    @ExpectedResult(inputJson = "[1,2,3,4,5]", expectedJson = "5")
    @ExpectedResult(inputJson = "[10, 20, 30, 40, 50]", expectedJson = "50")
    @ExpectedResult(inputJson = "[-10, -20, -30, -40, -5]", expectedJson = "-5")
    @ExpectedResult(inputJson = "[]", expectedJson = "__NULL__")
    public Integer findMaxValue(int[] arr) {
        if (arr == null || arr.length == 0) return null;
        int max = arr[0];
        for (int num : arr) {
            if (num > max) max = num;
        }
        return max;
    }
}