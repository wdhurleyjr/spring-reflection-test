package com.reflectiontest.springReflectionTest.repositories;

import com.reflectiontest.springReflectionTest.models.Product;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for Product data
 */
public interface ProductRepository {
    /**
     * Checks if a product exists by name
     */
    boolean existsByName(String name);

    /**
     * Finds a product by name
     */
    Optional<Product> findByName(String name);

    /**
     * Saves a product
     */
    Product save(Product product);

    /**
     * Deletes a product by name
     */
    void deleteByName(String name);
}

