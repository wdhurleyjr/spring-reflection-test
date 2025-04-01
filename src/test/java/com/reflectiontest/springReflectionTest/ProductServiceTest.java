package com.reflectiontest.springReflectionTest;

import com.reflectiontest.springReflectionTest.models.Product;
import com.reflectiontest.springReflectionTest.repositories.ProductRepository;
import com.reflectiontest.springReflectionTest.services.ProductService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product laptop;
    private Product mouse;
    private Product headphones;

    @BeforeEach
    void setUp() {
        laptop = new Product("Laptop", 1200.00);
        mouse = new Product("Mouse", 25.00);
        headphones = new Product("Headphones", 150.00);
    }

    @Test
    @Order(1)
    void testGetProductDetails() {
        // Scenario 1: Product exists
        when(productRepository.findByName("Laptop")).thenReturn(Optional.of(laptop));
        assertEquals(laptop, productService.getProductDetails(laptop));

        // Scenario 2: Product does not exist
        when(productRepository.findByName("NonExistent")).thenReturn(Optional.empty());
        assertNull(productService.getProductDetails(new Product("NonExistent", 0)));

        // Scenario 3: Null input
        assertNull(productService.getProductDetails(null));
    }

    @Test
    @Order(2)
    void testIsProductCached() {
        // Scenario 1: Product exists
        when(productRepository.existsByName("Mouse")).thenReturn(true);
        assertTrue(productService.isProductCached(mouse));

        // Scenario 2: Product does not exist
        when(productRepository.existsByName("Keyboard")).thenReturn(false);
        assertFalse(productService.isProductCached(new Product("Keyboard", 50.00)));

        // Scenario 3: Null input
        assertFalse(productService.isProductCached(null));
    }

    @Test
    @Order(3)
    void testAddAndCheckCache() {
        // Scenario 1: Successfully add and check product
        when(productRepository.existsByName("Headphones")).thenReturn(true);
        assertTrue(productService.addAndCheckCache(headphones));

        // Scenario 2: Null input
        assertFalse(productService.addAndCheckCache(null));
    }

    @Test
    @Order(4)
    void testFindProductsCheaperThan() {
        // Scenario 1: Multiple products under price
        List<Product> cheapProducts = Arrays.asList(mouse, headphones);
        when(productRepository.findByPriceLessThan(100.00)).thenReturn(cheapProducts);

        List<String> result = productService.findProductsCheaperThan(100.00);
        assertEquals(2, result.size());
        assertTrue(result.contains("Mouse"));
        assertTrue(result.contains("Headphones"));

        // Scenario 2: No products under price
        when(productRepository.findByPriceLessThan(10.00)).thenReturn(Collections.emptyList());
        assertTrue(productService.findProductsCheaperThan(10.00).isEmpty());
    }

    @Test
    @Order(5)
    void testFindProductsByNamePattern() {
        // Scenario 1: Products matching pattern
        List<Product> matchedProducts = Arrays.asList(
                new Product("Keyboard", 50.00),
                new Product("Headphones", 150.00)
        );
        when(productRepository.findProductsByNamePattern(".*key.*")).thenReturn(matchedProducts);

        List<String> result = productService.findProductsByNamePattern("key");
        assertEquals(2, result.size());
        assertTrue(result.contains("Keyboard"));
        assertTrue(result.contains("Headphones"));

        // Scenario 2: No products matching pattern
        when(productRepository.findProductsByNamePattern(".*non-existent.*")).thenReturn(Collections.emptyList());
        assertTrue(productService.findProductsByNamePattern("non-existent").isEmpty());
    }

    @Test
    @Order(6)
    void testDeleteProductAndVerify() {
        // Scenario 1: Successfully delete product
        Product deleteTest = new Product("DeleteTest", 99.99);
        when(productRepository.existsByName("DeleteTest")).thenReturn(false);

        assertTrue(productService.deleteProductAndVerify(deleteTest));

        // Verify repository methods were called
        verify(productRepository).save(deleteTest);
        verify(productRepository).deleteByName("DeleteTest");

        // Scenario 2: Null input
        assertFalse(productService.deleteProductAndVerify(null));
    }

    @Test
    @Order(7)
    void testIsProductInPriceRange() {
        // Scenario 1: Product in price range
        Product rangeTest = new Product("RangeTest", 75.00);
        List<Product> productsInRange = Collections.singletonList(rangeTest);
        when(productRepository.findByPriceBetween(50.0, 100.0)).thenReturn(productsInRange);

        assertTrue(productService.isProductInPriceRange(rangeTest));

        // Scenario 2: Product out of price range
        Product outOfRangeTest = new Product("OutOfRangeTest", 200.00);
        when(productRepository.findByPriceBetween(50.0, 100.0)).thenReturn(Collections.emptyList());

        assertFalse(productService.isProductInPriceRange(outOfRangeTest));

        // Scenario 3: Null input
        assertFalse(productService.isProductInPriceRange(null));
    }

    @Test
    @Order(8)
    void testCountTotalProducts() {
        // Scenario 1: Multiple products
        List<Product> allProducts = Arrays.asList(laptop, mouse, headphones);
        when(productRepository.findAll()).thenReturn(allProducts);

        assertEquals(3, productService.countTotalProducts(3));

        // Scenario 2: Limit less than total products
        assertEquals(2, productService.countTotalProducts(2));

        // Scenario 3: Zero limit
        assertEquals(0, productService.countTotalProducts(0));
    }
}
