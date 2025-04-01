package com.reflectiontest.springReflectionTest.repositories;

import com.reflectiontest.springReflectionTest.models.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {
    /**
     * Find a product by name
     */
    Optional<Product> findByName(String name);

    /**
     * Check if a product exists by name
     */
    boolean existsByName(String name);

    /**
     * Find products with price less than a given value
     */
    List<Product> findByPriceLessThan(double price);

    /**
     * Find products with price greater than a given value
     */
    List<Product> findByPriceGreaterThan(double price);

    /**
     * Find products within a price range
     */
    List<Product> findByPriceBetween(double minPrice, double maxPrice);

    /**
     * Custom query to find products with name containing a specific string
     */
    @Query("{'name': {$regex: ?0, $options: 'i'}}")
    List<Product> findProductsByNamePattern(String namePattern);

    /**
     * Delete a product by name
     */
    void deleteByName(String name);
}
