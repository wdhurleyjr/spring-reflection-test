package com.reflectiontest.springReflectionTest.testrunner.core;

import com.reflectiontest.springReflectionTest.models.Product;
import com.reflectiontest.springReflectionTest.models.User;
import com.reflectiontest.springReflectionTest.repositories.*;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic Mock Configuration Manager
 */
public class MockConfigurationManager {
    private static final Logger logger = LoggerFactory.getLogger(MockConfigurationManager.class);

    // Stores mock instances with their corresponding types
    private final Map<Class<?>, Object> mockInstances = new ConcurrentHashMap<>();

    /**
     * Creates and configures mocks for all repository interfaces
     */
    public void createAndConfigureMocks() {
        // Clear existing mocks
        mockInstances.clear();

        // Find and mock all repository interfaces
        List<Class<?>> repositoryInterfaces = findRepositoryInterfaces();

        for (Class<?> repoInterface : repositoryInterfaces) {
            createMockForRepository(repoInterface);
        }
    }

    /**
     * Finds all repository interfaces in the base package
     */
    private List<Class<?>> findRepositoryInterfaces() {
        List<Class<?>> repositories = new ArrayList<>();

        // You might want to use Reflections library here for more dynamic scanning
        repositories.add(UserRepository.class);
        repositories.add(ProductRepository.class);
        repositories.add(AuthenticationRepository.class);
        repositories.add(TokenRepository.class);
        repositories.add(SearchHistoryRepository.class);

        return repositories;
    }

    /**
     * Creates a mock for a specific repository
     */
    private <T> T createMockForRepository(Class<T> repositoryInterface) {
        T mockInstance = Mockito.mock(repositoryInterface);

        // Basic default mocking logic
        if (repositoryInterface == UserRepository.class) {
            configureUserRepositoryMock((UserRepository) mockInstance);
        } else if (repositoryInterface == ProductRepository.class) {
            configureProductRepositoryMock((ProductRepository) mockInstance);
        } else if (repositoryInterface == AuthenticationRepository.class) {
            configureAuthRepositoryMock((AuthenticationRepository) mockInstance);
        } else if (repositoryInterface == TokenRepository.class) {
            configureTokenRepositoryMock((TokenRepository) mockInstance);
        } else if (repositoryInterface == SearchHistoryRepository.class) {
            configureSearchHistoryRepositoryMock((SearchHistoryRepository) mockInstance);
        }

        // Store the mock
        mockInstances.put(repositoryInterface, mockInstance);

        return mockInstance;
    }

    // Implement configuration methods for each repository type
    private void configureUserRepositoryMock(UserRepository mock) {
        Mockito.when(mock.existsByUsername("johndoe")).thenReturn(true);
        Mockito.when(mock.findByUsername("johndoe"))
                .thenReturn(Optional.of(new User("johndoe", "email", "password", "user")));
    }

    private void configureProductRepositoryMock(ProductRepository mock) {
        Mockito.when(mock.existsByName("Laptop")).thenReturn(true);
        Mockito.when(mock.findByName("Laptop"))
                .thenReturn(Optional.of(new Product("Laptop", 1200.0)));
    }

    private void configureAuthRepositoryMock(AuthenticationRepository mock) {
        Mockito.when(mock.isAccountLocked("lockedUser")).thenReturn(true);
    }

    private void configureTokenRepositoryMock(TokenRepository mock) {
        Mockito.when(mock.generateResetToken("johndoe")).thenReturn("token123");
    }

    private void configureSearchHistoryRepositoryMock(SearchHistoryRepository mock) {
        Map<String, Long> searchCounts = new HashMap<>();
        searchCounts.put("Laptop", 5L);
        Mockito.when(mock.getSearchCounts()).thenReturn(searchCounts);
    }

    /**
     * Retrieves a mock instance for a specific type
     */
    public Object getMockForType(Class<?> type) {
        return mockInstances.get(type);
    }

    /**
     * Injects mocks into a service instance
     */
    public void injectMocksIntoInstance(Object serviceInstance) {
        if (serviceInstance == null) return;

        try {
            for (Field field : serviceInstance.getClass().getDeclaredFields()) {
                Object mockInstance = getMockForType(field.getType());

                if (mockInstance != null) {
                    field.setAccessible(true);
                    field.set(serviceInstance, mockInstance);
                    logger.debug("Injected mock for: {} in {}",
                            field.getType().getSimpleName(),
                            serviceInstance.getClass().getSimpleName());
                }
            }
        } catch (Exception e) {
            logger.error("Error injecting mocks: {}", e.getMessage(), e);
        }
    }
}