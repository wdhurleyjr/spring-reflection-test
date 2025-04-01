package com.reflectiontest.springReflectionTest.testrunner.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reflectiontest.springReflectionTest.annotations.TestObject;
import com.reflectiontest.springReflectionTest.annotations.TestObjectCreation;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
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

    // Stores test objects created from annotations
    private final Map<String, Object> testObjects = new ConcurrentHashMap<>();

    // Object mapper for JSON conversion
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates and configures mocks for all repository interfaces
     */
    public void createAndConfigureMocks() {
        // Clear existing mocks, behaviors, and test objects
        mockInstances.clear();
        defaultMethodBehaviors.clear();
        testObjects.clear();

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
     * Injects mocks into a service instance and processes test object annotations
     */
    public void injectMocksIntoInstance(Object serviceInstance) {
        if (serviceInstance == null) return;

        try {
            // Process TestObjectCreation annotation if present
            processTestObjectCreationAnnotation(serviceInstance);

            // Inject mocks
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

    /**
     * Process TestObjectCreation annotation on the service constructor
     */
    private void processTestObjectCreationAnnotation(Object serviceInstance) {
        Class<?> serviceClass = serviceInstance.getClass();

        // Look for annotations on constructors
        for (Constructor<?> constructor : serviceClass.getDeclaredConstructors()) {
            if (constructor.isAnnotationPresent(TestObjectCreation.class)) {
                TestObjectCreation annotation = constructor.getAnnotation(TestObjectCreation.class);
                processTestObjects(annotation, serviceInstance);
                break;
            }
        }
    }

    /**
     * Process test objects defined in the TestObjectCreation annotation
     */
    private void processTestObjects(TestObjectCreation annotation, Object serviceInstance) {
        if (annotation == null) return;

        TestObject[] testObjectDefs = annotation.objects();
        if (testObjectDefs == null || testObjectDefs.length == 0) return;

        logger.info("Processing {} test objects for {}",
                testObjectDefs.length, serviceInstance.getClass().getSimpleName());

        // Create test objects from definitions
        for (TestObject testObjectDef : testObjectDefs) {
            try {
                // Create object from JSON
                Object testObject = createTestObject(testObjectDef);
                if (testObject != null) {
                    // Store the test object by name
                    testObjects.put(testObjectDef.name(), testObject);

                    // Configure repository behaviors for this object
                    configureRepositoryBehaviorsForTestObject(testObject, testObjectDef);

                    logger.debug("Created test object: {} of type {}",
                            testObjectDef.name(), testObjectDef.type().getSimpleName());
                }
            } catch (Exception e) {
                logger.error("Error creating test object {}: {}",
                        testObjectDef.name(), e.getMessage(), e);
            }
        }
    }

    /**
     * Create a test object from its definition
     */
    private Object createTestObject(TestObject testObjectDef) {
        try {
            // Parse the JSON to create the object
            Object testObject = objectMapper.readValue(testObjectDef.json(), testObjectDef.type());

            // Apply field values if specified
            if (testObjectDef.fields() != null && testObjectDef.fields().length > 0) {
                applyFieldValues(testObject, testObjectDef.fields());
            }

            return testObject;
        } catch (Exception e) {
            logger.error("Failed to create test object {}: {}",
                    testObjectDef.name(), e.getMessage());
            return null;
        }
    }

    /**
     * Apply field values to a test object
     */
    private void applyFieldValues(Object testObject, String[] fieldValues) {
        if (testObject == null || fieldValues == null) return;

        for (String fieldValue : fieldValues) {
            try {
                // Parse field:value format
                String[] parts = fieldValue.split(":", 2);
                if (parts.length == 2) {
                    String fieldName = parts[0].trim();
                    String value = parts[1].trim();

                    // Set the field value via reflection
                    Field field = testObject.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);

                    // Convert value to appropriate type and set
                    Object convertedValue = convertValueToFieldType(value, field.getType());
                    field.set(testObject, convertedValue);
                }
            } catch (Exception e) {
                logger.warn("Error setting field value {}: {}", fieldValue, e.getMessage());
            }
        }
    }

    /**
     * Convert a string value to the appropriate field type
     */
    private Object convertValueToFieldType(String value, Class<?> fieldType) {
        if (value == null) return null;

        if (fieldType == String.class) {
            return value;
        } else if (fieldType == int.class || fieldType == Integer.class) {
            return Integer.parseInt(value);
        } else if (fieldType == long.class || fieldType == Long.class) {
            return Long.parseLong(value);
        } else if (fieldType == double.class || fieldType == Double.class) {
            return Double.parseDouble(value);
        } else if (fieldType == boolean.class || fieldType == Boolean.class) {
            return Boolean.parseBoolean(value);
        }

        // For complex types, try to use JSON parsing
        try {
            return objectMapper.readValue(value, fieldType);
        } catch (Exception e) {
            logger.warn("Failed to convert value to {}: {}", fieldType.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Configure repository behaviors for a test object
     */
    private void configureRepositoryBehaviorsForTestObject(Object testObject, TestObject testObjectDef) {
        if (testObject == null) return;

        Class<?> objectClass = testObject.getClass();
        String objectClassName = objectClass.getSimpleName().toLowerCase();

        // Find matching repository
        for (Class<?> repoInterface : mockInstances.keySet()) {
            String repoName = repoInterface.getSimpleName();
            if (repoName.toLowerCase().contains(objectClassName + "repository")) {
                configureRepositoryForObject(repoInterface, testObject);
            }
        }
    }

    /**
     * Configure a specific repository for a test object
     */
    private void configureRepositoryForObject(Class<?> repoInterface, Object testObject) {
        try {
            // Get common identifying field (usually "name" or "username")
            String idField = null;
            Object idValue = null;

            for (String possibleIdField : new String[]{"name", "username", "id", "code"}) {
                try {
                    Field field = testObject.getClass().getDeclaredField(possibleIdField);
                    field.setAccessible(true);
                    Object value = field.get(testObject);
                    if (value != null) {
                        idField = possibleIdField;
                        idValue = value;
                        break;
                    }
                } catch (NoSuchFieldException e) {
                    // Field doesn't exist, try next
                }
            }

            if (idField == null || idValue == null) {
                logger.warn("Could not find identifying field for {}", testObject.getClass().getSimpleName());
                return;
            }

            // Configure findBy methods
            configureRepositoryFindMethods(repoInterface, testObject, idField, idValue);

            // Configure existsBy methods
            configureRepositoryExistsMethods(repoInterface, idField, idValue);

            // Add to lists returned by findAll
            configureRepositoryFindAllMethod(repoInterface, testObject);

        } catch (Exception e) {
            logger.error("Error configuring repository for {}: {}",
                    testObject.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * Configure findBy methods for a repository
     */
    private void configureRepositoryFindMethods(Class<?> repoInterface, Object testObject,
                                                String idField, Object idValue) {
        // Configure findById
        String findByIdMethodName = "findById";
        try {
            Method findByIdMethod = repoInterface.getMethod(findByIdMethodName, Object.class);
            if (findByIdMethod != null) {
                Map<Object, Object> behaviorMap = new HashMap<>();
                behaviorMap.put(idValue, Optional.of(testObject));
                setDefaultMethodBehavior(repoInterface, findByIdMethodName, behaviorMap);
            }
        } catch (NoSuchMethodException e) {
            // Method doesn't exist, that's okay
        }

        // Configure findBy[IdField]
        String findByFieldMethodName = "findBy" + idField.substring(0, 1).toUpperCase() + idField.substring(1);
        try {
            Method findByFieldMethod = repoInterface.getMethod(findByFieldMethodName, idValue.getClass());
            if (findByFieldMethod != null) {
                Map<Object, Object> behaviorMap = new HashMap<>();
                behaviorMap.put(idValue, Optional.of(testObject));
                setDefaultMethodBehavior(repoInterface, findByFieldMethodName, behaviorMap);
            }
        } catch (NoSuchMethodException e) {
            // Method doesn't exist, that's okay
        }
    }

    /**
     * Configure existsBy methods for a repository
     */
    private void configureRepositoryExistsMethods(Class<?> repoInterface, String idField, Object idValue) {
        // Configure existsById
        String existsByIdMethodName = "existsById";
        try {
            Method existsByIdMethod = repoInterface.getMethod(existsByIdMethodName, Object.class);
            if (existsByIdMethod != null) {
                Map<Object, Object> behaviorMap = new HashMap<>();
                behaviorMap.put(idValue, true);
                setDefaultMethodBehavior(repoInterface, existsByIdMethodName, behaviorMap);
            }
        } catch (NoSuchMethodException e) {
            // Method doesn't exist, that's okay
        }

        // Configure existsBy[IdField]
        String existsByFieldMethodName = "existsBy" + idField.substring(0, 1).toUpperCase() + idField.substring(1);
        try {
            Method existsByFieldMethod = repoInterface.getMethod(existsByFieldMethodName, idValue.getClass());
            if (existsByFieldMethod != null) {
                Map<Object, Object> behaviorMap = new HashMap<>();
                behaviorMap.put(idValue, true);
                setDefaultMethodBehavior(repoInterface, existsByFieldMethodName, behaviorMap);
            }
        } catch (NoSuchMethodException e) {
            // Method doesn't exist, that's okay
        }
    }

    /**
     * Configure findAll method for a repository
     */
    private void configureRepositoryFindAllMethod(Class<?> repoInterface, Object testObject) {
        String findAllMethodName = "findAll";
        try {
            Method findAllMethod = repoInterface.getMethod(findAllMethodName);
            if (findAllMethod != null) {
                // Get existing behavior or create new list
                Map<String, Object> behaviors = defaultMethodBehaviors.computeIfAbsent(
                        repoInterface, k -> new HashMap<>());

                List<Object> resultList;
                if (behaviors.containsKey(findAllMethodName)) {
                    resultList = (List<Object>) behaviors.get(findAllMethodName);
                } else {
                    resultList = new ArrayList<>();
                    behaviors.put(findAllMethodName, resultList);
                }

                // Add the test object if not already in the list
                if (!resultList.contains(testObject)) {
                    resultList.add(testObject);
                }
            }
        } catch (NoSuchMethodException e) {
            // Method doesn't exist, that's okay
        }
    }

    /**
     * Get a test object by name
     */
    public Object getTestObject(String name) {
        return testObjects.get(name);
    }

    /**
     * Get all test objects of a specific type
     */
    public List<Object> getTestObjectsByType(Class<?> type) {
        return testObjects.values().stream()
                .filter(obj -> type.isAssignableFrom(obj.getClass()))
                .collect(Collectors.toList());
    }
}