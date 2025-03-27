package com.reflectiontest.springReflectionTest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.reflectiontest.springReflectionTest.annotations.ExpectedResult;
import com.reflectiontest.springReflectionTest.annotations.MockDependency;
import com.reflectiontest.springReflectionTest.metrics.MetricsCollector;
import com.reflectiontest.springReflectionTest.models.Product;
import com.reflectiontest.springReflectionTest.models.User;
import com.reflectiontest.springReflectionTest.reporting.TestReport;
import com.reflectiontest.springReflectionTest.reporting.TestReportBuilder;
import com.reflectiontest.springReflectionTest.repositories.AuthenticationRepository;
import com.reflectiontest.springReflectionTest.repositories.ExternalProductRepository;
import com.reflectiontest.springReflectionTest.repositories.ProductRepository;
import com.reflectiontest.springReflectionTest.repositories.SearchHistoryRepository;
import com.reflectiontest.springReflectionTest.repositories.TokenRepository;
import com.reflectiontest.springReflectionTest.repositories.UserRepository;
import com.reflectiontest.springReflectionTest.util.BytecodeHashUtil;
import org.mockito.Mockito;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Simplified Unit Test Runner
 */
public class UnitTestRunner {
    private static final Logger logger = LoggerFactory.getLogger(UnitTestRunner.class);
    private static final ObjectMapper objectMapper = configureObjectMapper();
    private static final Map<Class<?>, Object> mockInstances = new HashMap<>();
    private static final String NULL_PLACEHOLDER = "__NULL__";

    // Configuration properties
    private final boolean collectMetrics;
    private final boolean verifyBytecodeHash;
    private final String basePackage;

    // Services
    private final MetricsCollector metricsCollector;
    private final TestReportBuilder reportBuilder;

    /**
     * Configure ObjectMapper with necessary modules and settings
     */
    private static ObjectMapper configureObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new SimpleModule());
        return mapper;
    }

    // Simplified constructor
    public UnitTestRunner() {
        this(detectBasePackage(), true, true);
    }

    // Constructor with configuration options
    public UnitTestRunner(String basePackage, boolean collectMetrics, boolean verifyBytecodeHash) {
        this.basePackage = basePackage;
        this.collectMetrics = collectMetrics;
        this.verifyBytecodeHash = verifyBytecodeHash;

        this.metricsCollector = new MetricsCollector();
        this.reportBuilder = new TestReportBuilder();

        logger.info("Initializing UnitTestRunner with: basePackage={}, collectMetrics={}, verifyBytecodeHash={}",
                basePackage, collectMetrics, verifyBytecodeHash);
    }

    /**
     * Runs all unit tests in the specified package
     *
     * @return TestReport containing all test results
     */
    public TestReport runTests() {
        logger.info("🔍 Starting test run in package: {}", basePackage);

        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> serviceClasses = reflections.getTypesAnnotatedWith(Service.class);

        logger.info("Found {} service classes to test", serviceClasses.size());

        // Create mocks for the known repository interfaces
        createAndConfigureMocks();

        // Runs tests sequentially
        for (Class<?> clazz : serviceClasses) {
            try {
                // Create instance with constructor or field injection
                Object serviceInstance = createServiceInstance(clazz);
                if (serviceInstance != null) {
                    runTestsForClass(serviceInstance);
                }
            } catch (Exception e) {
                logger.error("Error instantiating class {}: {}", clazz.getName(), e.getMessage());
                reportBuilder.addError(clazz.getName(), "Class initialization failed", e);
            }
        }

        if (collectMetrics) {
            metricsCollector.recordTotalExecutionTime(System.nanoTime() - metricsCollector.getStartTime());
            reportBuilder.setMetrics(metricsCollector.getMetrics());
        }

        return reportBuilder.build();
    }

    /**
     * Creates and configures mocks for all repository interfaces
     */
    private void createAndConfigureMocks() {
        // UserRepository
        UserRepository userRepo = Mockito.mock(UserRepository.class);
        mockInstances.put(UserRepository.class, userRepo);

        // Configure specific behavior
        Mockito.when(userRepo.existsByUsername("johndoe")).thenReturn(true);
        Mockito.when(userRepo.existsByUsername("admin")).thenReturn(true);
        Mockito.when(userRepo.existsByUsername("doesnotexist")).thenReturn(false);
        Mockito.when(userRepo.existsByUsername("newuser")).thenReturn(false);

        // For any other username
        Mockito.when(userRepo.existsByUsername(Mockito.argThat(arg ->
                arg != null && !arg.equals("johndoe") && !arg.equals("admin") &&
                        !arg.equals("doesnotexist") && !arg.equals("newuser"))
        )).thenReturn(false);

        // findByUsername mock
        User johndoe = new User("johndoe", "johndoe@example.com", "password123", "user");
        User admin = new User("admin", "admin@system.com", "admin123", "admin");

        Mockito.when(userRepo.findByUsername("johndoe")).thenReturn(Optional.of(johndoe));
        Mockito.when(userRepo.findByUsername("admin")).thenReturn(Optional.of(admin));
        Mockito.when(userRepo.findByUsername(Mockito.argThat(arg ->
                arg != null && !arg.equals("johndoe") && !arg.equals("admin"))
        )).thenReturn(Optional.empty());

        // Save mock
        Mockito.when(userRepo.save(Mockito.any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // AuthenticationRepository
        AuthenticationRepository authRepo = Mockito.mock(AuthenticationRepository.class);
        mockInstances.put(AuthenticationRepository.class, authRepo);

        Mockito.when(authRepo.isAccountLocked("lockedUser")).thenReturn(true);
        Mockito.when(authRepo.isAccountLocked(Mockito.argThat(arg ->
                arg != null && !arg.equals("lockedUser"))
        )).thenReturn(false);

        Mockito.doNothing().when(authRepo).recordLoginAttempt(
                Mockito.anyString(), Mockito.anyBoolean(), Mockito.anyString());

        Mockito.when(authRepo.getFailedLoginAttempts("lockedUser")).thenReturn(5);
        Mockito.when(authRepo.getFailedLoginAttempts("repeatOffender")).thenReturn(3);
        Mockito.when(authRepo.getFailedLoginAttempts(Mockito.argThat(arg ->
                arg != null && !arg.equals("lockedUser") && !arg.equals("repeatOffender"))
        )).thenReturn(0);

        // TokenRepository
        TokenRepository tokenRepo = Mockito.mock(TokenRepository.class);
        mockInstances.put(TokenRepository.class, tokenRepo);

        Mockito.when(tokenRepo.generateResetToken("johndoe")).thenReturn("token123");
        Mockito.when(tokenRepo.generateResetToken("admin")).thenReturn("token456");
        Mockito.when(tokenRepo.generateResetToken("newuser")).thenReturn("token789");
        Mockito.when(tokenRepo.generateResetToken(Mockito.anyString())).thenReturn("defaultToken");

        Mockito.when(tokenRepo.validateResetToken("johndoe", "token123")).thenReturn(true);
        Mockito.when(tokenRepo.validateResetToken("admin", "token456")).thenReturn(true);
        Mockito.when(tokenRepo.validateResetToken(Mockito.anyString(), Mockito.anyString())).thenReturn(false);

        Mockito.doNothing().when(tokenRepo).invalidateResetToken(Mockito.anyString());

        // ProductRepository
        ProductRepository productRepo = Mockito.mock(ProductRepository.class);
        mockInstances.put(ProductRepository.class, productRepo);

        Mockito.when(productRepo.existsByName("Laptop")).thenReturn(true);
        Mockito.when(productRepo.existsByName("Mouse")).thenReturn(true);
        Mockito.when(productRepo.existsByName(Mockito.argThat(arg ->
                arg != null && !arg.equals("Laptop") && !arg.equals("Mouse"))
        )).thenReturn(false);

        Product laptop = new Product("Laptop", 1200.00);
        Product mouse = new Product("Mouse", 25.00);

        Mockito.when(productRepo.findByName("Laptop")).thenReturn(Optional.of(laptop));
        Mockito.when(productRepo.findByName("Mouse")).thenReturn(Optional.of(mouse));
        Mockito.when(productRepo.findByName(Mockito.argThat(arg ->
                arg != null && !arg.equals("Laptop") && !arg.equals("Mouse"))
        )).thenReturn(Optional.empty());

        Mockito.when(productRepo.save(Mockito.any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            // Update our specific mock behavior for existsByName
            if (product != null && product.getName() != null) {
                Mockito.when(productRepo.existsByName(product.getName())).thenReturn(true);
            }
            return product;
        });

        Mockito.doNothing().when(productRepo).deleteByName(Mockito.anyString());

        // SearchHistoryRepository
        SearchHistoryRepository searchRepo = Mockito.mock(SearchHistoryRepository.class);
        mockInstances.put(SearchHistoryRepository.class, searchRepo);

        Map<String, Long> searchCounts = new LinkedHashMap<>();
        searchCounts.put("Laptop", 5L);
        searchCounts.put("Mouse", 3L);
        searchCounts.put("Keyboard", 2L);
        searchCounts.put("Headphones", 1L);
        searchCounts.put("Monitor", 1L);

        Mockito.when(searchRepo.getSearchCounts()).thenReturn(searchCounts);
        Mockito.doNothing().when(searchRepo).saveSearch(Mockito.anyString());
        Mockito.doNothing().when(searchRepo).clearHistory();

        // ExternalProductRepository (if needed)
        ExternalProductRepository externalProductRepo = Mockito.mock(ExternalProductRepository.class);
        mockInstances.put(ExternalProductRepository.class, externalProductRepo);

        Mockito.when(externalProductRepo.existsByName(Mockito.anyString())).thenReturn(false);
    }

    /**
     * Creates a service instance with mocked dependencies
     */
    private Object createServiceInstance(Class<?> clazz) {
        try {
            // Try constructor injection first
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                Class<?>[] paramTypes = constructor.getParameterTypes();
                if (paramTypes.length > 0) {
                    boolean canInject = true;
                    Object[] args = new Object[paramTypes.length];

                    for (int i = 0; i < paramTypes.length; i++) {
                        if (!mockInstances.containsKey(paramTypes[i])) {
                            canInject = false;
                            break;
                        }
                        args[i] = mockInstances.get(paramTypes[i]);
                    }

                    if (canInject) {
                        constructor.setAccessible(true);
                        Object instance = constructor.newInstance(args);
                        logger.info("Created instance of {} using constructor injection", clazz.getSimpleName());
                        return instance;
                    }
                }
            }

            // Fallback to default constructor and field injection
            Object instance = clazz.getDeclaredConstructor().newInstance();
            injectMocks(instance);
            logger.info("Created instance of {} using field injection", clazz.getSimpleName());
            return instance;
        } catch (Exception e) {
            logger.error("Could not create instance of {}: {}",
                    clazz.getSimpleName(), e.getMessage(), e);
            reportBuilder.addError(clazz.getName(), "Instance creation failed", e);
            return null;
        }
    }

    /**
     * Injects mock dependencies into fields marked with @MockDependency
     */
    private void injectMocks(Object serviceInstance) {
        try {
            for (Field field : serviceInstance.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(MockDependency.class)) {
                    field.setAccessible(true);
                    Object mockInstance = mockInstances.get(field.getType());

                    if (mockInstance != null) {
                        field.set(serviceInstance, mockInstance);
                        logger.debug("Injected mock for: {} in {}",
                                field.getType().getSimpleName(),
                                serviceInstance.getClass().getSimpleName());
                    } else {
                        logger.warn("No mock available for {}", field.getType().getSimpleName());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error injecting mocks: {}", e.getMessage(), e);
        }
    }

    /**
     * Runs tests for a specific service class instance
     */
    private void runTestsForClass(Object serviceInstance) {
        Class<?> clazz = serviceInstance.getClass();

        for (Method method : clazz.getDeclaredMethods()) {
            ExpectedResult[] testCases = method.getAnnotationsByType(ExpectedResult.class);
            if (testCases.length > 0) {
                logger.info("Running tests for: {}.{}", clazz.getSimpleName(), method.getName());

                if (verifyBytecodeHash) {
                    verifyMethodBytecode(clazz, method);
                }

                for (ExpectedResult testCase : testCases) {
                    runTest(serviceInstance, method, testCase.inputJson(), testCase.expectedJson());
                }
            }
        }
    }

    /**
     * Runs a single test case for a method
     */
    private void runTest(Object serviceInstance, Method method, String inputJson, String expectedJsonValue) {
        String methodId = serviceInstance.getClass().getName() + "." + method.getName();

        try {
            long startTime = System.nanoTime();
            Class<?>[] paramTypes = method.getParameterTypes();
            Object[] methodParams;

            // Handle multi-parameter vs. single-parameter methods
            if (paramTypes.length == 1) {
                methodParams = new Object[]{parseInput(inputJson, paramTypes[0])};
            } else {
                methodParams = parseMultipleInputs(inputJson, paramTypes);
            }

            // Check if test expects an exception
            boolean expectsException = "__THROWS__".equals(expectedJsonValue);

            try {
                Object actualOutput = method.invoke(serviceInstance, methodParams);
                long endTime = System.nanoTime();
                long duration = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

                if (collectMetrics) {
                    metricsCollector.recordTestExecution(methodId, duration);
                }

                if (expectsException) {
                    reportBuilder.addFailure(serviceInstance.getClass().getName(), method.getName(),
                            inputJson, "Expected exception but none was thrown", null, duration);
                    logger.error("❌ FAILED: {}({}) - Expected exception but method completed normally",
                            method.getName(), inputJson);
                    metricsCollector.recordTestFailed();
                } else {
                    // Compare expected and actual outputs
                    Object expectedOutput = parseExpectedOutput(expectedJsonValue, method.getReturnType());
                    String serializedExpected = objectMapper.writeValueAsString(expectedOutput);
                    String serializedActual = objectMapper.writeValueAsString(actualOutput);

                    // Handle Optional format issues
                    if (Optional.class.isAssignableFrom(method.getReturnType())) {
                        serializedExpected = expectedJsonValue;
                        serializedActual = formatOptionalOutput(actualOutput);
                    }

                    // Try both exact string comparison and content comparison
                    if (serializedExpected.equals(serializedActual) || compareJsonContents(serializedExpected, serializedActual)) {
                        reportBuilder.addSuccess(serviceInstance.getClass().getName(), method.getName(),
                                inputJson, serializedExpected, serializedActual, duration);
                        logger.info("✅ PASSED: {}({}) -> {} [{}ms]",
                                method.getName(), inputJson, actualOutput, duration);
                        metricsCollector.recordTestPassed();
                    } else {
                        reportBuilder.addFailure(serviceInstance.getClass().getName(), method.getName(),
                                inputJson, serializedExpected, serializedActual, duration);
                        logger.error("❌ FAILED: {}({}) - Expected: {}, Actual: {} [{}ms]",
                                method.getName(), inputJson, expectedOutput, actualOutput, duration);
                        metricsCollector.recordTestFailed();
                    }
                }
            } catch (InvocationTargetException e) {
                Throwable cause = e.getTargetException();
                long endTime = System.nanoTime();
                long duration = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

                if (collectMetrics) {
                    metricsCollector.recordTestExecution(methodId, duration);
                }

                if (expectsException) {
                    reportBuilder.addSuccess(serviceInstance.getClass().getName(), method.getName(),
                            inputJson, "Exception: " + cause.getClass().getSimpleName(),
                            "Exception: " + cause.getClass().getSimpleName(), duration);
                    logger.info("✅ PASSED: {}({}) -> Exception: {} [{}ms]",
                            method.getName(), inputJson, cause.getClass().getSimpleName(), duration);
                    metricsCollector.recordTestPassed();
                } else {
                    reportBuilder.addError(serviceInstance.getClass().getName(), method.getName(),
                            inputJson, cause.getClass().getSimpleName() + ": " + cause.getMessage(), duration);
                    logger.error("❌ ERROR: {}({}) - Unexpected Exception: {} - {}",
                            method.getName(), inputJson, cause.getClass().getSimpleName(), cause.getMessage());
                    metricsCollector.recordTestError();
                }
            }
        } catch (Exception e) {
            reportBuilder.addError(serviceInstance.getClass().getName(), method.getName(),
                    inputJson, e.getClass().getSimpleName() + ": " + e.getMessage(), 0);
            logger.error("❌ ERROR: {}({}) - Exception: {} - {}",
                    method.getName(), inputJson, e.getClass().getSimpleName(), e.getMessage());
            metricsCollector.recordTestError();
        }
    }

    /**
     * Parses multiple parameters from a JSON array string
     */
    private Object[] parseMultipleInputs(String inputJson, Class<?>[] paramTypes) throws JsonProcessingException {
        Object[] inputs = objectMapper.readValue(inputJson, Object[].class);
        Object[] methodParams = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            if (i < inputs.length) {
                String paramJson = objectMapper.writeValueAsString(inputs[i]);
                methodParams[i] = parseInput(paramJson, paramTypes[i]);
            } else {
                methodParams[i] = null;
            }
        }

        return methodParams;
    }

    /**
     * Parses the expected output
     */
    private Object parseExpectedOutput(String json, Class<?> targetType) throws JsonProcessingException {
        // Special handling for Optional
        if (Optional.class.isAssignableFrom(targetType)) {
            return json; // Return raw JSON string to handle expected format
        }

        return parseInput(json, targetType);
    }

    /**
     * Parses input value from JSON string
     */
    private Object parseInput(String json, Class<?> targetType) throws JsonProcessingException {
        if (json == null || json.equals(NULL_PLACEHOLDER)) {
            return null;
        }

        // Handle special token replacements (Integer.MAX_VALUE, etc.)
        json = replaceSpecialTokens(json);

        // Handle primitive types
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (json.equals("true") || json.equals("\"true\"")) return true;
            if (json.equals("false") || json.equals("\"false\"")) return false;
        }

        if (targetType == int.class || targetType == Integer.class) {
            try {
                return Integer.parseInt(json.replace("\"", ""));
            } catch (NumberFormatException e) {
                // Not a simple integer, continue with normal parsing
            }
        }

        // Handle specific model classes
        if (targetType == User.class) {
            try {
                return objectMapper.readValue(json, User.class);
            } catch (Exception e) {
                logger.warn("Failed to parse User from JSON: {}", e.getMessage());
                // Continue with normal parsing as fallback
            }
        }

        if (targetType == Product.class) {
            try {
                return objectMapper.readValue(json, Product.class);
            } catch (Exception e) {
                logger.warn("Failed to parse Product from JSON: {}", e.getMessage());
                // Continue with normal parsing as fallback
            }
        }

        // Special handling for collections with generic types
        if (Collection.class.isAssignableFrom(targetType) && json.startsWith("[")) {
            // Try to infer the element type from the JSON content
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(
                    List.class, inferElementType(json)));
        }

        // Handle strings that might come with or without quotes
        if (targetType == String.class) {
            if (json.startsWith("\"") && json.endsWith("\"")) {
                return json.substring(1, json.length() - 1);
            }
            return json;
        }

        return objectMapper.readValue(json, targetType);
    }

    /**
     * Special handling for optional formatting
     */
    private String formatOptionalOutput(Object output) throws JsonProcessingException {
        if (output == null) {
            return "null";
        }

        if (output instanceof Optional<?>) {
            Optional<?> optional = (Optional<?>) output;
            if (optional.isPresent()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("present", true);
                result.put("value", optional.get());
                return objectMapper.writeValueAsString(result);
            } else {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("present", false);
                return objectMapper.writeValueAsString(result);
            }
        }

        return objectMapper.writeValueAsString(output);
    }

    /**
     * Runs tests for a specific service class
     */
    public TestReport runTestsForClass(String className) {
        createAndConfigureMocks();

        try {
            Class<?> clazz = Class.forName(className);
            if (!clazz.isAnnotationPresent(Service.class)) {
                logger.warn("Class {} is not a Service", className);
                return reportBuilder.build();
            }

            // Create service instance
            Object serviceInstance = createServiceInstance(clazz);
            if (serviceInstance != null) {
                runTestsForClass(serviceInstance);
            }

            if (collectMetrics) {
                reportBuilder.setMetrics(metricsCollector.getMetrics());
            }

            return reportBuilder.build();
        } catch (Exception e) {
            logger.error("Error running tests for class {}: {}", className, e.getMessage());
            reportBuilder.addError(className, "Class initialization failed", e);
            return reportBuilder.build();
        }
    }

    /**
     * Runs tests for a specific method
     */
    public TestReport runTestsForMethod(String className, String methodName) {
        createAndConfigureMocks();

        try {
            Class<?> clazz = Class.forName(className);
            if (!clazz.isAnnotationPresent(Service.class)) {
                logger.warn("Class {} is not a Service", className);
                return reportBuilder.build();
            }

            // Create service instance
            Object serviceInstance = createServiceInstance(clazz);
            if (serviceInstance == null) {
                logger.error("Could not create instance of {}", className);
                return reportBuilder.build();
            }

            Method method = null;
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    method = m;
                    break;
                }
            }

            if (method == null) {
                logger.warn("Method {} not found in class {}", methodName, className);
                return reportBuilder.build();
            }

            ExpectedResult[] testCases = method.getAnnotationsByType(ExpectedResult.class);
            if (testCases.length > 0) {
                logger.info("Running tests for: {}.{}", clazz.getSimpleName(), method.getName());

                if (verifyBytecodeHash) {
                    verifyMethodBytecode(clazz, method);
                }

                for (ExpectedResult testCase : testCases) {
                    runTest(serviceInstance, method, testCase.inputJson(), testCase.expectedJson());
                }
            } else {
                logger.warn("No tests found for method {}.{}", className, methodName);
            }

            if (collectMetrics) {
                reportBuilder.setMetrics(metricsCollector.getMetrics());
            }

            return reportBuilder.build();
        } catch (Exception e) {
            logger.error("Error running tests for method {}.{}: {}", className, methodName, e.getMessage());
            reportBuilder.addError(className, methodName, "N/A", e.getMessage(), 0);
            return reportBuilder.build();
        }
    }

    /**
     * Automatically detects the base package by checking the stack trace
     */
    private static String detectBasePackage() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if (element.getClassName().startsWith("com.")) {
                String className = element.getClassName();
                int lastDotIndex = className.lastIndexOf('.');
                return lastDotIndex > 0 ? className.substring(0, lastDotIndex) : className;
            }
        }
        return "com"; // Default to scanning all `com.` packages
    }

    /**
     * Replace special tokens like Integer.MAX_VALUE with actual values
     */
    private String replaceSpecialTokens(String json) {
        if (json == null) {
            return null;
        }

        // Replace Integer constants
        json = json.replace("Integer.MAX_VALUE", String.valueOf(Integer.MAX_VALUE));
        json = json.replace("Integer.MIN_VALUE", String.valueOf(Integer.MIN_VALUE));

        // Replace other constants if needed
        json = json.replace("Long.MAX_VALUE", String.valueOf(Long.MAX_VALUE));
        json = json.replace("Long.MIN_VALUE", String.valueOf(Long.MIN_VALUE));

        return json;
    }

    /**
     * Infer the element type for collections based on JSON content
     */
    private Class<?> inferElementType(String json) {
        // Simplistic inference based on JSON content
        if (json.contains("\"name\"") && json.contains("\"price\"")) {
            // Looks like a Product
            return findClassByName("Product");
        } else if (json.contains("\"username\"") && json.contains("\"email\"")) {
            // Looks like a User
            return findClassByName("User");
        } else {
            // Default to Object
            return Object.class;
        }
    }

    /**
     * Find a class by name in the base package
     */
    private Class<?> findClassByName(String className) {
        try {
            // Try with fully qualified name
            return Class.forName("com.reflectiontest.springReflectionTest.models." + className);
        } catch (ClassNotFoundException e) {
            // Fall back to Object if not found
            return Object.class;
        }
    }

    /**
     * Compare JSON objects for equality, ignoring property order
     */
    private boolean compareJsonContents(String expected, String actual) {
        try {
            // Parse both to Maps or Lists
            if (expected.startsWith("{") && actual.startsWith("{")) {
                Map<String, Object> expectedMap = objectMapper.readValue(expected, Map.class);
                Map<String, Object> actualMap = objectMapper.readValue(actual, Map.class);
                return compareMaps(expectedMap, actualMap);
            } else if (expected.startsWith("[") && actual.startsWith("[")) {
                List<Object> expectedList = objectMapper.readValue(expected, List.class);
                List<Object> actualList = objectMapper.readValue(actual, List.class);
                return compareLists(expectedList, actualList);
            }
        } catch (Exception e) {
            logger.debug("Failed to compare as JSON objects: {}", e.getMessage());
        }
        // Fall back to string comparison
        return expected.equals(actual);
    }

    /**
     * Recursively compare maps, handling nested objects
     */
    private boolean compareMaps(Map<String, Object> expected, Map<String, Object> actual) {
        if (expected.size() != actual.size()) return false;

        for (String key : expected.keySet()) {
            if (!actual.containsKey(key)) return false;

            Object expectedValue = expected.get(key);
            Object actualValue = actual.get(key);

            if (expectedValue == null && actualValue == null) {
                continue;
            }

            if (expectedValue == null || actualValue == null) {
                return false;
            }

            if (expectedValue instanceof Map && actualValue instanceof Map) {
                if (!compareMaps((Map<String, Object>)expectedValue, (Map<String, Object>)actualValue)) {
                    return false;
                }
            } else if (expectedValue instanceof List && actualValue instanceof List) {
                if (!compareLists((List<Object>)expectedValue, (List<Object>)actualValue)) {
                    return false;
                }
            } else if (!expectedValue.equals(actualValue)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Compare lists (order-sensitive)
     */
    private boolean compareLists(List<Object> expected, List<Object> actual) {
        if (expected.size() != actual.size()) return false;

        for (int i = 0; i < expected.size(); i++) {
            Object expectedItem = expected.get(i);
            Object actualItem = actual.get(i);

            if (expectedItem instanceof Map && actualItem instanceof Map) {
                if (!compareMaps((Map<String, Object>)expectedItem, (Map<String, Object>)actualItem)) {
                    return false;
                }
            } else if (expectedItem instanceof List && actualItem instanceof List) {
                if (!compareLists((List<Object>)expectedItem, (List<Object>)actualItem)) {
                    return false;
                }
            } else if (!expectedItem.equals(actualItem)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Verifies if method bytecode has changed compared to the stored hash
     */
    private void verifyMethodBytecode(Class<?> clazz, Method method) {
        try {
            String currentHash = BytecodeHashUtil.generateMethodHash(clazz, method);
            String storedHash = BytecodeHashUtil.getStoredMethodHash(clazz, method);

            if (storedHash != null && !currentHash.equals(storedHash)) {
                logger.warn("⚠️ Method bytecode has changed: {}.{}",
                        clazz.getSimpleName(), method.getName());
                reportBuilder.addWarning(clazz.getName(), method.getName(),
                        "Method bytecode has changed since tests were generated");
            } else if (storedHash == null) {
                // Store hash if it doesn't exist
                BytecodeHashUtil.storeMethodHash(clazz, method, currentHash);
            }
        } catch (Exception e) {
            logger.error("Error verifying bytecode for {}.{}: {}",
                    clazz.getSimpleName(), method.getName(), e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            // Run all tests with default configuration
            UnitTestRunner runner = new UnitTestRunner("com.reflectiontest.springReflectionTest", false, true);
            TestReport report = runner.runTests();
            System.out.println(report.getSummary());
        } else if (args.length == 1) {
            // Run tests for specific class
            UnitTestRunner runner = new UnitTestRunner("com.reflectiontest.springReflectionTest", false, true);
            TestReport report = runner.runTestsForClass(args[0]);
            System.out.println(report.getSummary());
        } else if (args.length == 2) {
            // Run tests for specific method
            UnitTestRunner runner = new UnitTestRunner("com.reflectiontest.springReflectionTest", false, true);
            TestReport report = runner.runTestsForMethod(args[0], args[1]);
            System.out.println(report.getSummary());
        } else {
            System.out.println("Usage: UnitTestRunner [className] [methodName]");
        }
    }
}