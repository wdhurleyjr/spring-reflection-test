package com.reflectiontest.springReflectionTest;

import com.reflectiontest.springReflectionTest.services.ProductService;
import com.reflectiontest.springReflectionTest.models.Product;
import com.reflectiontest.springReflectionTest.repositories.ProductRepository;
import com.reflectiontest.springReflectionTest.repositories.SearchHistoryRepository;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SearchHistoryRepository searchHistoryRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        productService = new ProductService(productRepository, searchHistoryRepository);

        when(productRepository.existsByName("Mouse")).thenReturn(true);
        when(productRepository.existsByName("Laptop")).thenReturn(true);
        when(productRepository.existsByName("Tablet")).thenReturn(true);
        when(productRepository.existsByName("Smartphone")).thenReturn(true);
        when(productRepository.findByName("Mouse")).thenReturn(Optional.of(new Product("Mouse", 25.0)));
        when(productRepository.findByName("Laptop")).thenReturn(Optional.of(new Product("Laptop", 1200.0)));
        when(productRepository.findByName("Keyboard")).thenReturn(Optional.of(new Product("Keyboard", 50.0)));
        when(productRepository.findByName("Monitor")).thenReturn(Optional.of(new Product("Monitor", 300.0)));
        when(productRepository.findByName("Headphones")).thenReturn(Optional.of(new Product("Headphones", 150.0)));

        Map<String, Long> searchCounts = new LinkedHashMap<>();
        searchCounts.put("Laptop", 5L);
        searchCounts.put("Mouse", 3L);
        searchCounts.put("Keyboard", 2L);
        searchCounts.put("Headphones", 1L);
        searchCounts.put("Monitor", 1L);
        when(searchHistoryRepository.getSearchCounts()).thenReturn(searchCounts);
    }

    @Test
    @Order(1)
    void testGetProductDetails() {
        assertEquals("Laptop", productService.getProductDetails(new Product("Laptop", 1200.0)).getName());
        assertEquals("Mouse", productService.getProductDetails(new Product("Mouse", 25.0)).getName());
        assertNull(productService.getProductDetails(null));
        assertNull(productService.getProductDetails(new Product(null, 50.0)));
    }

    @Test
    @Order(2)
    void testIsProductCached() {
        assertFalse(productService.isProductCached(new Product("Keyboard", 50.0)));
        assertFalse(productService.isProductCached(new Product("Monitor", 300.0)));
        assertTrue(productService.isProductCached(new Product("Mouse", 25.0)));
        assertFalse(productService.isProductCached(null));
    }

    @Test
    @Order(3)
    void testAddAndCheckCache() {
        assertTrue(productService.addAndCheckCache(new Product("Headphones", 150.0)));
        assertTrue(productService.addAndCheckCache(new Product("Webcam", 75.0)));
        assertFalse(productService.addAndCheckCache(null));
    }

    @Test
    @Order(4)
    void testAddProductTwiceAndCheck() {
        assertTrue(productService.addProductTwiceAndCheck(new Product("Tablet", 300.0)));
        assertTrue(productService.addProductTwiceAndCheck(new Product("Smartphone", 800.0)));
    }

    @Test
    @Order(5)
    void testGetTopSearchedProducts() {
        assertEquals(List.of("Laptop", "Mouse", "Keyboard"), productService.getTopSearchedProducts(3));
        assertEquals(List.of("Laptop"), productService.getTopSearchedProducts(1));
        assertEquals(List.of(), productService.getTopSearchedProducts(0));
        assertEquals(List.of("Laptop", "Mouse", "Keyboard", "Headphones", "Monitor"), productService.getTopSearchedProducts(10));
    }
}
