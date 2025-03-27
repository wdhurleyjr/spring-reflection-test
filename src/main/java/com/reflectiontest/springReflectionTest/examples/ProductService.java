package com.reflectiontest.springReflectionTest.examples;

import com.reflectiontest.springReflectionTest.annotations.ExpectedResult;
import com.reflectiontest.springReflectionTest.annotations.IntegrationTest;
import com.reflectiontest.springReflectionTest.annotations.MockDependency;
import com.reflectiontest.springReflectionTest.models.Product;
import com.reflectiontest.springReflectionTest.repositories.ExternalProductRepository;
import com.reflectiontest.springReflectionTest.repositories.ProductRepository;
import com.reflectiontest.springReflectionTest.repositories.SearchHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Product service that demonstrates various testing scenarios
 * including complex objects, collections, exceptions, and edge cases.
 *
 * Implementation is stateless - relies on injected repositories for all data access.
 */
@Service
public class ProductService {

    @MockDependency
    private ExternalProductRepository externalProductRepository;

    @MockDependency
    private ProductRepository productRepository;

    @MockDependency
    private SearchHistoryRepository searchRepository;

    /**
     * Adds a product to the repository if it doesn't already exist
     */
    public void addProduct(Product product) {
        if (product == null || product.getName() == null) {
            return;
        }
        if (!externalProductRepository.existsByName(product.getName())) {
            productRepository.save(product);
        }
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
        Optional<Product> existingProduct = productRepository.findByName(product.getName());
        if (existingProduct.isPresent()) {
            return existingProduct.get();
        }

        // Check if it exists in external repository
        if (externalProductRepository.existsByName(product.getName())) {
            // In a real scenario, we might fetch details from external repository
            // For simplicity, we'll use the provided product object
            addProduct(product);
            return product;
        }

        // Not found in either repository
        return null;
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

        // Check both repositories
        return productRepository.existsByName(product.getName()) ||
                externalProductRepository.existsByName(product.getName());
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

    /**
     * Tests with multi-value collections as return types
     */
    @ExpectedResult(inputJson = "3", expectedJson = "[\"Laptop\", \"Mouse\", \"Keyboard\"]")
    @ExpectedResult(inputJson = "1", expectedJson = "[\"Laptop\"]")
    @ExpectedResult(inputJson = "0", expectedJson = "[]")
    @ExpectedResult(inputJson = "10", expectedJson = "[\"Laptop\", \"Mouse\", \"Keyboard\", \"Monitor\", \"Headphones\"]")
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

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalItems", products.size());
        summary.put("totalValue", totalValue);

        return summary;
    }

    // Standard computational tests
    @ExpectedResult(inputJson = "[5, 3]", expectedJson = "8")
    @ExpectedResult(inputJson = "[-2, 7]", expectedJson = "5")
    @ExpectedResult(inputJson = "[0, 0]", expectedJson = "0")
    @ExpectedResult(inputJson = "[2147483647, 1]", expectedJson = "-2147483648") // Integer.MAX_VALUE + 1 = Integer.MIN_VALUE
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
    @ExpectedResult(inputJson = "[-2147483648, 0, 2147483647]", expectedJson = "2147483647") // Using actual integer values instead of constants
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

        // Check our repository first
        Optional<Product> product = productRepository.findByName(name);
        if (product.isPresent()) {
            return product;
        }

        // If not found and exists in external repository, create a product instance
        if (externalProductRepository.existsByName(name)) {
            // In a real scenario, we would fetch details from external repository
            // For now, we'll create a placeholder product with estimated price
            double estimatedPrice = estimatePrice(name);
            return Optional.of(new Product(name, estimatedPrice));
        }

        return Optional.empty();
    }

    /**
     * Helper method to estimate a price based on product name
     * This is a stand-in for actual pricing logic that might exist in a real service
     */
    private double estimatePrice(String productName) {
        if ("Laptop".equalsIgnoreCase(productName)) {
            return 1200.0;
        } else if ("Mouse".equalsIgnoreCase(productName)) {
            return 25.0;
        } else if ("Keyboard".equalsIgnoreCase(productName)) {
            return 50.0;
        } else if ("Monitor".equalsIgnoreCase(productName)) {
            return 300.0;
        } else if ("Headphones".equalsIgnoreCase(productName)) {
            return 150.0;
        } else {
            // Default price for unknown products
            return 99.99;
        }
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
        LinkedHashMap<String, Object> container = new LinkedHashMap<>();
        container.put("items", items);
        container.put("count", items.size());
        return container;
    }
}
