package com.reflectiontest.springReflectionTest.examples;

import com.reflectiontest.springReflectionTest.annotations.ExpectedResult;
import com.reflectiontest.springReflectionTest.annotations.IntegrationTest;
import com.reflectiontest.springReflectionTest.annotations.MockDependency;
import com.reflectiontest.springReflectionTest.models.Product;
import com.reflectiontest.springReflectionTest.repositories.ExternalProductRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Product service that demonstrates various testing scenarios
 * including complex objects, collections, exceptions, and edge cases.
 */
@Service
public class ProductService {

    @MockDependency
    private ExternalProductRepository productRepository;

    private final Map<String, Product> productCache = new HashMap<>();
    private final List<String> searchHistory = new ArrayList<>();

    // Accessory method
    public void addProduct(Product product) {
        if (product == null || product.getName() == null) {
            return;
        }
        if (!productRepository.existsByName(product.getName())) {
            productCache.putIfAbsent(product.getName(), product);
        }
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "{\"name\": \"Laptop\", \"price\": 1200.00}", expectedJson = "{\"name\": \"Laptop\", \"price\": 1200.00}")
    @ExpectedResult(inputJson = "{\"name\": \"Mouse\", \"price\": 25.00}", expectedJson = "{\"name\": \"Mouse\", \"price\": 25.00}")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "__NULL__")
    @ExpectedResult(inputJson = "{\"name\": null, \"price\": 50.00}", expectedJson = "__NULL__")
    public Product getProductDetails(Product product) {
        if (product == null || product.getName() == null) return null;

        // Track search history
        searchHistory.add(product.getName());

        if (!productCache.containsKey(product.getName())) {
            if (productRepository.existsByName(product.getName())) {
                return product;
            }
            addProduct(product);
        }

        return productCache.get(product.getName());
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "{\"name\": \"Keyboard\", \"price\": 50.00}", expectedJson = "false")
    @ExpectedResult(inputJson = "{\"name\": \"Monitor\", \"price\": 300.00}", expectedJson = "false")
    @ExpectedResult(inputJson = "{\"name\": \"Mouse\", \"price\": 25.00}", expectedJson = "true")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "false")
    public boolean isProductCached(Product product) {
        return product != null && (productCache.containsKey(product.getName()) || productRepository.existsByName(product.getName()));
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "{\"name\": \"Headphones\", \"price\": 150.00}", expectedJson = "true")
    @ExpectedResult(inputJson = "{\"name\": \"Webcam\", \"price\": 75.00}", expectedJson = "true")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "false")
    public boolean addAndCheckCache(Product product) {
        addProduct(product);
        return product != null && (productCache.containsKey(product.getName()) || productRepository.existsByName(product.getName()));
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "{\"name\": \"Tablet\", \"price\": 300.00}", expectedJson = "true")
    @ExpectedResult(inputJson = "{\"name\": \"Smartphone\", \"price\": 800.00}", expectedJson = "true")
    public boolean addProductTwiceAndCheck(Product product) {
        addProduct(product);
        addProduct(product);
        return productCache.containsKey(product.getName());
    }

    /**
     * Tests with multi-value collections as return types
     */
    @ExpectedResult(inputJson = "3", expectedJson = "[\"Laptop\", \"Mouse\", \"Keyboard\"]")
    @ExpectedResult(inputJson = "1", expectedJson = "[\"Laptop\"]")
    @ExpectedResult(inputJson = "0", expectedJson = "[]")
    @ExpectedResult(inputJson = "10", expectedJson = "[\"Laptop\", \"Mouse\", \"Keyboard\", \"Monitor\", \"Headphones\"]")
    public List<String> getTopSearchedProducts(int limit) {
        Map<String, Long> counts = searchHistory.stream()
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Tests with complex nested objects
     */
    @ExpectedResult(
            inputJson = "[{\"name\": \"Laptop\", \"price\": 1200.00}, {\"name\": \"Mouse\", \"price\": 25.00}]",
            expectedJson = "{\"totalItems\": 2, \"totalValue\": 1225.00}"
    )
    @ExpectedResult(
            inputJson = "[]",
            expectedJson = "{\"totalItems\": 0, \"totalValue\": 0.0}"
    )
    @ExpectedResult(
            inputJson = "[{\"name\": \"Budget PC\", \"price\": 500.00}, {\"name\": \"Premium PC\", \"price\": 2000.00}, {\"name\": \"Mid-range PC\", \"price\": 1000.00}]",
            expectedJson = "{\"totalItems\": 3, \"totalValue\": 3500.00}"
    )
    public Map<String, Object> calculateOrderSummary(List<Product> products) {
        double totalValue = products.stream()
                .mapToDouble(Product::getPrice)
                .sum();

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalItems", products.size());
        summary.put("totalValue", totalValue);

        return summary;
    }

    // Standard computational tests
    @ExpectedResult(inputJson = "[5, 3]", expectedJson = "8")
    @ExpectedResult(inputJson = "[-2, 7]", expectedJson = "5")
    @ExpectedResult(inputJson = "[0, 0]", expectedJson = "0")
    @ExpectedResult(inputJson = "[Integer.MAX_VALUE, 1]", expectedJson = "Integer.MIN_VALUE")
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

    /**
     * Tests with expected exceptions
     */
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

    /**
     * Tests with custom exceptions
     */
    @ExpectedResult(inputJson = "1000.0", expectedJson = "1230.0")
    @ExpectedResult(inputJson = "0.0", expectedJson = "0.0")
    @ExpectedResult(inputJson = "-100.0", expectedJson = "__THROWS__")
    public double calculatePriceWithTax(double price) {
        if (price < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        return price * 1.23; // 23% tax
    }

    /**
     * String manipulation tests
     */
    @ExpectedResult(inputJson = "\"hello\"", expectedJson = "\"olleh\"")
    @ExpectedResult(inputJson = "\"Test\"", expectedJson = "\"tseT\"")
    @ExpectedResult(inputJson = "\"\"", expectedJson = "\"\"")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "__NULL__")
    @ExpectedResult(inputJson = "\"12345\"", expectedJson = "\"54321\"")
    @ExpectedResult(inputJson = "\"A man, a plan, a canal: Panama\"", expectedJson = "\"amanaP :lanac a ,nalp a ,nam A\"")
    public String reverseString(String input) {
        if (input == null) return null;
        return new StringBuilder(input).reverse().toString();
    }

    /**
     * Tests with arrays and null handling
     */
    @ExpectedResult(inputJson = "[1,2,3,4,5]", expectedJson = "5")
    @ExpectedResult(inputJson = "[10, 20, 30, 40, 50]", expectedJson = "50")
    @ExpectedResult(inputJson = "[-10, -20, -30, -40, -5]", expectedJson = "-5")
    @ExpectedResult(inputJson = "[]", expectedJson = "__NULL__")
    @ExpectedResult(inputJson = "[5]", expectedJson = "5")
    @ExpectedResult(inputJson = "[Integer.MIN_VALUE, 0, Integer.MAX_VALUE]", expectedJson = "Integer.MAX_VALUE")
    public Integer findMaxValue(int[] arr) {
        if (arr == null || arr.length == 0) return null;
        int max = arr[0];
        for (int num : arr) {
            if (num > max) max = num;
        }
        return max;
    }

    /**
     * Tests with optional return types
     */
    @ExpectedResult(inputJson = "\"Laptop\"", expectedJson = "{\"present\":true,\"value\":{\"name\":\"Laptop\",\"price\":1200.0}}")
    @ExpectedResult(inputJson = "\"KeyboardNotFound\"", expectedJson = "{\"present\":false}")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "{\"present\":false}")
    public Optional<Product> findProductByName(String name) {
        if (name == null) return Optional.empty();

        // Add some test products
        if (!productCache.containsKey("Laptop")) {
            productCache.put("Laptop", new Product("Laptop", 1200.0));
        }

        return Optional.ofNullable(productCache.get(name));
    }

    /**
     * Tests with generic types
     */
    @ExpectedResult(
            inputJson = "[\"apple\", \"banana\", \"cherry\"]",
            expectedJson = "{\"items\":[\"apple\",\"banana\",\"cherry\"],\"count\":3}"
    )
    @ExpectedResult(
            inputJson = "[]",
            expectedJson = "{\"items\":[],\"count\":0}"
    )
    @ExpectedResult(
            inputJson = "[\"single\"]",
            expectedJson = "{\"items\":[\"single\"],\"count\":1}"
    )
    public <T> Map<String, Object> wrapInContainer(List<T> items) {
        Map<String, Object> container = new HashMap<>();
        container.put("items", items);
        container.put("count", items.size());
        return container;
    }
}
