package com.reflectiontest.springReflectionTest.repositories;

import com.reflectiontest.springReflectionTest.annotations.MockReturn;
import com.reflectiontest.springReflectionTest.annotations.MockReturns;
import com.reflectiontest.springReflectionTest.models.Product;
import java.util.Optional;

/**
 * Repository for Product data with mock return annotations
 */
public interface ProductRepository {
    /**
     * Checks if a product exists by name
     */
    @MockReturns({
            @MockReturn(inputJson = "\"Laptop\"", returnJson = "true"),
            @MockReturn(inputJson = "\"Mouse\"", returnJson = "true"),
            @MockReturn(inputJson = "\"NonExistentProduct\"", returnJson = "false", isDefault = true)
    })
    boolean existsByName(String name);

    /**
     * Finds a product by name
     */
    @MockReturns({
            @MockReturn(inputJson = "\"Laptop\"",
                    returnJson = "{\"name\":\"Laptop\",\"price\":1200.00}",
                    isDefault = false),
            @MockReturn(inputJson = "\"Mouse\"",
                    returnJson = "{\"name\":\"Mouse\",\"price\":25.00}",
                    isDefault = false),
            @MockReturn(inputJson = "\"NonExistentProduct\"",
                    returnJson = "null",
                    isDefault = true)
    })
    Optional<Product> findByName(String name);

    /**
     * Saves a product
     */
    @MockReturns({
            @MockReturn(inputJson = "{\"name\":\"Laptop\",\"price\":1200.00}",
                    returnJson = "{\"name\":\"Laptop\",\"price\":1200.00}"),
            @MockReturn(inputJson = "{\"name\":\"Mouse\",\"price\":25.00}",
                    returnJson = "{\"name\":\"Mouse\",\"price\":25.00}")
    })
    Product save(Product product);

    /**
     * Deletes a product by name
     */
    @MockReturns({
            @MockReturn(inputJson = "\"Laptop\"", returnJson = ""),
            @MockReturn(inputJson = "\"Mouse\"", returnJson = "")
    })
    void deleteByName(String name);
}

