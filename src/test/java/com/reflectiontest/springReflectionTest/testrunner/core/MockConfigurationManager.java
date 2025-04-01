package com.reflectiontest.springReflectionTest.testrunner.core;

import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.reflections.Reflections;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Dynamic Mock Configuration Manager with flexible repository mocking
 */
public class MockConfigurationManager {
    private static final Logger logger = LoggerFactory.getLogger(MockConfigurationManager.class);

    // Stores mock instances with their corresponding types
    private final Map<Class<?>, Object> mockInstances = new ConcurrentHashMap<>();

    // Stores default method behaviors
    private final Map<Class<?>, Map<String, Object>> defaultMethodBehaviors = new ConcurrentHashMap<>();

    /**
     * Creates and configures mocks for all repository interfaces
     */
    public void createAndConfigureMocks() {
        // Clear existing mocks and behaviors
        mockInstances.clear();
        defaultMethodBehaviors.clear();

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
        Reflections reflections = new Reflections("com.reflectiontest.springReflectionTest");

        Set<Class<?>> repositoryInterfaces = new HashSet<>();
        repositoryInterfaces.addAll(reflections.getTypesAnnotatedWith(Repository.class));

        repositoryInterfaces.removeIf(clazz ->
                !MongoRepository.class.isAssignableFrom(clazz) ||
                        !clazz.isInterface()
        );

        logger.info("Discovered {} repository interfaces", repositoryInterfaces.size());
        return new ArrayList<>(repositoryInterfaces);
    }

    /**
     * Creates a mock for a specific repository
     */
    private <T> T createMockForRepository(Class<T> repositoryInterface) {
        T mockInstance = Mockito.mock(repositoryInterface, invocation -> {
            Method method = invocation.getMethod();
            String methodName = method.getName();
            Object[] args = invocation.getArguments();

            // Check for predefined method behavior
            Map<String, Object> classBehaviors = defaultMethodBehaviors.get(repositoryInterface);
            if (classBehaviors != null) {
                Object predefinedBehavior = findMatchingBehavior(classBehaviors, methodName, args);
                if (predefinedBehavior != null) {
                    return predefinedBehavior;
                }
            }

            // Default return values
            return getDefaultReturnValue(method.getReturnType());
        });

        // Store the mock
        mockInstances.put(repositoryInterface, mockInstance);

        logger.debug("Created mock for repository: {}", repositoryInterface.getSimpleName());
        return mockInstance;
    }

    /**
     * Finds a matching predefined behavior for a method
     */
    private Object findMatchingBehavior(Map<String, Object> classBehaviors,
                                        String methodName,
                                        Object[] args) {
        // Look for method-specific behavior
        Object methodBehavior = classBehaviors.get(methodName);

        if (methodBehavior instanceof List) {
            // For methods that return lists, filter based on predicate
            if (args.length > 0 && methodBehavior instanceof List) {
                return ((List<?>) methodBehavior).stream()
                        .filter(item -> matchesPredicate(item, args[0]))
                        .collect(Collectors.toList());
            }
            return methodBehavior;
        }

        // For methods with specific argument matching
        if (methodBehavior instanceof Map) {
            Map<?, ?> behaviorMap = (Map<?, ?>) methodBehavior;
            for (Map.Entry<?, ?> entry : behaviorMap.entrySet()) {
                if (matchesPredicate(entry.getKey(), args[0])) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    /**
     * Checks if an item matches a predicate
     */
    private boolean matchesPredicate(Object item, Object predicate) {
        if (item == null || predicate == null) {
            return item == predicate;
        }

        // Basic equality check
        if (item.equals(predicate)) {
            return true;
        }

        // For regex matching of strings
        if (item instanceof String && predicate instanceof String) {
            String itemStr = (String) item;
            String predicateStr = (String) predicate;
            return itemStr.contains(predicateStr);
        }

        return false;
    }

    /**
     * Sets default behavior for a repository method
     * @param repositoryClass Repository interface class
     * @param methodName Method name
     * @param behavior Behavior to return (can be List, Map, or direct value)
     */
    public void setDefaultMethodBehavior(
            Class<?> repositoryClass,
            String methodName,
            Object behavior
    ) {
        defaultMethodBehaviors.computeIfAbsent(
                repositoryClass,
                k -> new HashMap<>()
        ).put(methodName, behavior);

        logger.debug("Set default behavior for {}.{}",
                repositoryClass.getSimpleName(),
                methodName
        );
    }

    /**
     * Provides default return values based on return type
     */
    private Object getDefaultReturnValue(Class<?> returnType) {
        if (returnType == Optional.class) {
            return Optional.empty();
        } else if (returnType == List.class) {
            return Collections.emptyList();
        } else if (returnType == Boolean.class || returnType == boolean.class) {
            return false;
        } else if (returnType == Integer.class || returnType == int.class) {
            return 0;
        } else if (returnType == Long.class || returnType == long.class) {
            return 0L;
        }

        // For other types, return null
        return null;
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