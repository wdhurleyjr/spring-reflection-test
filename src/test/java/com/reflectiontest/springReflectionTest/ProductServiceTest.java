package com.reflectiontest.springReflectionTest;

import com.reflectiontest.springReflectionTest.examples.ProductService;
import com.reflectiontest.springReflectionTest.models.Product;
import com.reflectiontest.springReflectionTest.repositories.ExternalProductRepository;

import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProductServiceTest {

    @Mock
    private ExternalProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @Order(1)
    void testGetProductDetails() {
        Product product1 = new Product("Laptop", 1200.00);
        Product product2 = new Product("Mouse", 25.00);

        when(productRepository.existsByName(anyString())).thenReturn(true);

        assertEquals(product1.getName(), productService.getProductDetails(product1).getName());
        assertEquals(product2.getName(), productService.getProductDetails(product2).getName());
        assertNull(productService.getProductDetails(null));

        verify(productRepository, atLeastOnce()).existsByName(anyString());
    }

    @Test
    @Order(2)
    void testIsProductCached() {
        Product product1 = new Product("Keyboard", 50.00);
        Product product2 = new Product("Monitor", 300.00);
        Product product3 = new Product("Mouse", 25.00);

        when(productRepository.existsByName("Keyboard")).thenReturn(false);
        when(productRepository.existsByName("Monitor")).thenReturn(false);
        when(productRepository.existsByName("Mouse")).thenReturn(true);

        assertFalse(productService.isProductCached(product1));
        assertFalse(productService.isProductCached(product2));
        assertTrue(productService.isProductCached(product3));
        assertFalse(productService.isProductCached(null));
    }

    @Test
    @Order(3)
    void testAddAndCheckCache() {
        Product product1 = new Product("Headphones", 150.00);
        Product product2 = new Product("Webcam", 75.00);

        assertFalse(productService.isProductCached(product1));
        assertFalse(productService.isProductCached(product2));

        productService.addProduct(product1);
        productService.addProduct(product2);

        when(productRepository.existsByName("Headphones")).thenReturn(true);
        when(productRepository.existsByName("Webcam")).thenReturn(true);

        assertTrue(productService.isProductCached(product1));
        assertTrue(productService.isProductCached(product2));
        assertFalse(productService.addAndCheckCache(null));
    }

    @Test
    @Order(4)
    void testAddProductTwiceAndCheck() {
        Product product1 = new Product("Tablet", 300.00);
        Product product2 = new Product("Smartphone", 800.00);

        when(productRepository.existsByName(anyString())).thenReturn(false);

        productService.addProduct(product1);
        productService.addProduct(product1);
        assertTrue(productService.isProductCached(product1));

        productService.addProduct(product2);
        productService.addProduct(product2);
        assertTrue(productService.isProductCached(product2));
    }

    @Test
    @Order(5)
    void testAddTwoNumbers() {
        assertEquals(8, productService.addTwoNumbers(5, 3));
        assertEquals(5, productService.addTwoNumbers(-2, 7));
        assertEquals(0, productService.addTwoNumbers(0, 0));
    }

    @Test
    @Order(6)
    void testMultiplyByTen() {
        assertEquals(50, productService.multiplyByTen(5));
        assertEquals(-30, productService.multiplyByTen(-3));
        assertEquals(0, productService.multiplyByTen(0));
        assertEquals(100, productService.multiplyByTen(10));
        assertEquals(10, productService.multiplyByTen(1));
    }

    @Test
    @Order(7)
    void testSubtractTwoNumbers() {
        assertEquals(5, productService.subtractTwoNumbers(10, 5));
        assertEquals(-7, productService.subtractTwoNumbers(3, 10));
        assertEquals(-5, productService.subtractTwoNumbers(0, 5));
    }

    @Test
    @Order(8)
    void testDivideTwoNumbers() {
        assertEquals(5.0, productService.divideTwoNumbers(10.0, 2.0), 0.01);
        assertEquals(-10.0, productService.divideTwoNumbers(-100.0, 10.0), 0.01);
        assertEquals(0.0, productService.divideTwoNumbers(0.0, 5.0), 0.01);
    }

    @Test
    @Order(9)
    void testDivideByZero() {
        Exception exception = assertThrows(ArithmeticException.class, () -> {
            productService.divideTwoNumbers(10.0, 0.0);
        });
        assertEquals("Cannot divide by zero", exception.getMessage());
    }

    @Test
    @Order(10)
    void testReverseString() {
        assertEquals("olleh", productService.reverseString("hello"));
        assertEquals("tseT", productService.reverseString("Test"));
        assertEquals("", productService.reverseString(""));
        assertNull(productService.reverseString(null));
    }

    @Test
    @Order(11)
    void testFindMaxValue() {
        assertEquals(5, productService.findMaxValue(new int[]{1, 2, 3, 4, 5}));
        assertEquals(50, productService.findMaxValue(new int[]{10, 20, 30, 40, 50}));
        assertEquals(-5, productService.findMaxValue(new int[]{-10, -20, -30, -40, -5}));
        assertNull(productService.findMaxValue(new int[]{}));
    }
}








