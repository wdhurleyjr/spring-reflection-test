package com.reflectiontest.springReflectionTest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.reflectiontest.springReflectionTest.annotations.ExpectedResult;
import com.reflectiontest.springReflectionTest.annotations.MockDependency;
import com.reflectiontest.springReflectionTest.metrics.MetricsCollector;
import com.reflectiontest.springReflectionTest.models.Product;
import com.reflectiontest.springReflectionTest.models.User;
import com.reflectiontest.springReflectionTest.repositories.*;
import com.reflectiontest.springReflectionTest.reporting.TestReport;
import com.reflectiontest.springReflectionTest.reporting.TestReportBuilder;
import com.reflectiontest.springReflectionTest.util.BytecodeHashUtil;
import com.reflectiontest.springReflectionTest.util.TestCaseUtil;
import org.mockito.Mockito;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced Unit Test Runner that executes reflection-based tests with performance metrics.
 * Supports parallel execution, reporting, and bytecode verification.
 */
public class UnitTestRunner {
    private static final Logger logger = LoggerFactory.getLogger(UnitTestRunner.class);
    private static final ObjectMapper objectMapper = configureObjectMapper();
    private static final Map<Class<?>, Object> mockInstances = new HashMap<>();
    private static final String NULL_PLACEHOLDER = "__NULL__";
    private static final int DEFAULT_THREADS = Runtime.getRuntime().availableProcessors();

    // Configuration properties
    private final boolean parallelExecution;
    private final boolean collectMetrics;
    private final boolean verifyBytecodeHash;
    private final int maxThreads;
    private final String basePackage;

    // Services
    private final MetricsCollector metricsCollector;
    private final TestReportBuilder reportBuilder;

    /**
     * Configure ObjectMapper with necessary modules and settings
     */
    private static ObjectMapper configureObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Add support for Java 8 features like Optional
        mapper.registerModule(new Jdk8Module());
        // Configure mapper for consistent serialization
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        // Handle unknown properties gracefully
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Configure Optional serialization to match expected format
        SimpleModule module = new SimpleModule();
        mapper.registerModule(module);

        return mapper;
    }

    // Test runner constructor with default configuration
    public UnitTestRunner() {
        this(detectBasePackage(), true, true, false, DEFAULT_THREADS);
    }

    // Test runner constructor with custom configuration
    public UnitTestRunner(String basePackage, boolean parallelExecution, boolean collectMetrics,
                          boolean verifyBytecodeHash, int maxThreads) {
        this.basePackage = basePackage;
        this.parallelExecution = parallelExecution;
        this.collectMetrics = collectMetrics;
        this.verifyBytecodeHash = verifyBytecodeHash;
        this.maxThreads = maxThreads;

        this.metricsCollector = new MetricsCollector();
        this.reportBuilder = new TestReportBuilder();

        logger.info("Initializing UnitTestRunner with: parallelExecution={}, collectMetrics={}, " +
                        "verifyBytecodeHash={}, maxThreads={}, basePackage={}",
                parallelExecution, collectMetrics, verifyBytecodeHash, maxThreads, basePackage);
    }

    /**
     * Runs all unit tests in the specified package
     *
     * @return TestReport containing all test results
     */
    public TestReport runTests() {
        logger.info("🔍 Starting test run in package: {}", basePackage);
        long startTime = System.nanoTime();

        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> serviceClasses = reflections.getTypesAnnotatedWith(Service.class);

        logger.info("Found {} service classes to test", serviceClasses.size());

        if (parallelExecution) {
            return runTestsInParallel(serviceClasses);
        } else {
            return runTestsSequentially(serviceClasses);
        }
    }

    /**
     * Runs tests for all service classes sequentially
     */
    private TestReport runTestsSequentially(Set<Class<?>> serviceClasses) {
        logger.info("Running tests sequentially");

        for (Class<?> clazz : serviceClasses) {
            try {
                // Create all mocks first
                createAllMocks(clazz);

                Object serviceInstance = clazz.getDeclaredConstructor().newInstance();
                injectMocks(serviceInstance);

                runTestsForClass(serviceInstance);
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
     * Create all needed mocks for a class
     */
    private void createAllMocks(Class<?> clazz) {
        try {
            // Find all fields with @MockDependency
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(MockDependency.class)) {
                    Class<?> fieldType = field.getType();

                    // Create mock if not already created
                    if (!mockInstances.containsKey(fieldType)) {
                        Object mockInstance = Mockito.mock(fieldType);
                        mockInstances.put(fieldType, mockInstance);
                    }
                }
            }

            // Set up default behaviors for all mocks
            setupDefaultMockBehavior();

        } catch (Exception e) {
            logger.error("Error creating mocks for class {}: {}", clazz.getName(), e.getMessage());
        }
    }

    /**
     * Runs tests for all service classes in parallel
     */
    private TestReport runTestsInParallel(Set<Class<?>> serviceClasses) {
        logger.info("Running tests in parallel with {} threads", maxThreads);

        // Create all mocks first to avoid concurrent mocking issues
        for (Class<?> clazz : serviceClasses) {
            createAllMocks(clazz);
        }

        // Set up mock behavior once for all threads
        setupDefaultMockBehavior();

        // Use try-with-resources to properly close the ExecutorService
        try (var executorResource = new ExecutorServiceResource(maxThreads)) {
            ExecutorService executor = executorResource.getExecutor();
            List<Future<?>> futures = new ArrayList<>();

            for (Class<?> clazz : serviceClasses) {
                final Class<?> finalClass = clazz; // Make effectively final for lambda
                futures.add(executor.submit(() -> {
                    try {
                        Object serviceInstance = finalClass.getDeclaredConstructor().newInstance();
                        injectMocks(serviceInstance);

                        runTestsForClass(serviceInstance);
                    } catch (Exception e) {
                        logger.error("Error instantiating class {}: {}", finalClass.getName(), e.getMessage());
                        reportBuilder.addError(finalClass.getName(), "Class initialization failed", e);
                    }
                }));
            }

            // Wait for all test executions to complete
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    logger.error("Error executing test in parallel: {}", e.getMessage());
                }
            }
        } // ExecutorService will be shutdown here automatically

        if (collectMetrics) {
            metricsCollector.recordTotalExecutionTime(System.nanoTime() - metricsCollector.getStartTime());
            reportBuilder.setMetrics(metricsCollector.getMetrics());
        }

        return reportBuilder.build();
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

    /**
     * Set up default mock behavior for repositories
     * This simplified version avoids concurrency issues
     */
    private void setupDefaultMockBehavior() {
        try {
            // Product Repository mock setup
            if (mockInstances.containsKey(ProductRepository.class)) {
                ProductRepository productRepo = (ProductRepository) mockInstances.get(ProductRepository.class);

                // Set up product repository behavior - use simple when/thenReturn syntax
                Product laptop = new Product("Laptop", 1200.0);
                Product mouse = new Product("Mouse", 25.0);

                Mockito.when(productRepo.existsByName("Mouse")).thenReturn(true);
                Mockito.when(productRepo.existsByName("Headphones")).thenReturn(true);
                Mockito.when(productRepo.existsByName("Webcam")).thenReturn(true);
                Mockito.when(productRepo.existsByName("Tablet")).thenReturn(true);
                Mockito.when(productRepo.existsByName("Smartphone")).thenReturn(true);

                Mockito.when(productRepo.findByName("Laptop")).thenReturn(Optional.of(laptop));
                Mockito.when(productRepo.findByName("Mouse")).thenReturn(Optional.of(mouse));

                // Configure save method to return the same product
                Mockito.when(productRepo.save(Mockito.any(Product.class))).thenAnswer(i -> i.getArgument(0));
            }

            // External Product Repository mock setup
            if (mockInstances.containsKey(ExternalProductRepository.class)) {
                ExternalProductRepository externalRepo = (ExternalProductRepository) mockInstances.get(ExternalProductRepository.class);

                // Set up external repository behavior
                Mockito.when(externalRepo.existsByName("Laptop")).thenReturn(true);
            }

            // Search History Repository mock setup
            if (mockInstances.containsKey(SearchHistoryRepository.class)) {
                SearchHistoryRepository searchRepo = (SearchHistoryRepository) mockInstances.get(SearchHistoryRepository.class);

                // Set up search counts for getTopSearchedProducts
                Map<String, Long> searchCounts = new LinkedHashMap<>();
                searchCounts.put("Laptop", 5L);
                searchCounts.put("Mouse", 3L);
                searchCounts.put("Keyboard", 2L);
                searchCounts.put("Monitor", 1L);
                searchCounts.put("Headphones", 1L);

                Mockito.when(searchRepo.getSearchCounts()).thenReturn(searchCounts);
            }

            // User Repository mock setup
            if (mockInstances.containsKey(UserRepository.class)) {
                UserRepository userRepo = (UserRepository) mockInstances.get(UserRepository.class);

                // Create test user
                User johnDoe = new User("johndoe", "johndoe@example.com", "password123", "USER");

                // Handle the registerUser test case specially
                if (currentMethodName.get() != null && currentMethodName.get().contains("registerUser")) {
                    Mockito.when(userRepo.existsByUsername("johndoe")).thenReturn(false);
                } else {
                    Mockito.when(userRepo.existsByUsername("johndoe")).thenReturn(true);
                }

                Mockito.when(userRepo.findByUsername("johndoe")).thenReturn(Optional.of(johnDoe));

                // Configure save method to return the same user
                Mockito.when(userRepo.save(Mockito.any(User.class))).thenAnswer(i -> i.getArgument(0));
            }

            // Authentication Repository mock setup
            if (mockInstances.containsKey(AuthenticationRepository.class)) {
                AuthenticationRepository authRepo = (AuthenticationRepository) mockInstances.get(AuthenticationRepository.class);

                // Set up authentication repository behavior
                Mockito.when(authRepo.isAccountLocked("lockedUser")).thenReturn(true);
            }

            // Token Repository mock setup
            if (mockInstances.containsKey(TokenRepository.class)) {
                TokenRepository tokenRepo = (TokenRepository) mockInstances.get(TokenRepository.class);

                // Set up token repository behavior
                Mockito.when(tokenRepo.generateResetToken("johndoe")).thenReturn("token123");
                Mockito.when(tokenRepo.validateResetToken("johndoe", "token123")).thenReturn(true);
            }
        } catch (Exception e) {
            logger.error("Error setting up mock behavior: {}", e.getMessage());
        }
    }

    // Thread local to track the current method being tested
    private static final ThreadLocal<String> currentMethodName = new ThreadLocal<>();

    /**
     * Check if we're in the context of registerUser test
     */
    private boolean isRegisterUserContext() {
        String methodName = currentMethodName.get();
        return methodName != null && methodName.contains("registerUser");
    }

    /**
     * Injects mock dependencies into a service instance
     */
    private void injectMocks(Object serviceInstance) throws IllegalAccessException {
        for (Field field : serviceInstance.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(MockDependency.class)) {
                field.setAccessible(true);

                Object mockInstance = mockInstances.get(field.getType());
                if (mockInstance != null) {
                    field.set(serviceInstance, mockInstance);
                    logger.debug("Injected mock for: {} in {}",
                            field.getType().getSimpleName(), serviceInstance.getClass().getSimpleName());
                }
            }
        }
    }

    /**
     * Executes a single test case for a method
     */
    private void runTest(Object serviceInstance, Method method, String inputJson, String expectedJsonValue) {
        String methodId = serviceInstance.getClass().getName() + "." + method.getName();
        String testCaseId = TestCaseUtil.generateTestCaseId(methodId, inputJson, expectedJsonValue);

        try {
            // Set current method name for context
            currentMethodName.set(methodId);

            // Reset mocks before each test to ensure independent executions
            for (Object mock : mockInstances.values()) {
                Mockito.reset(mock);
            }

            // Set up mock behavior for this test
            setupDefaultMockBehavior();

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
                    } else {
                        reportBuilder.addFailure(serviceInstance.getClass().getName(), method.getName(),
                                inputJson, serializedExpected, serializedActual, duration);
                        logger.error("❌ FAILED: {}({}) - Expected: {}, Actual: {} [{}ms]",
                                method.getName(), inputJson, expectedOutput, actualOutput, duration);
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
                } else {
                    reportBuilder.addError(serviceInstance.getClass().getName(), method.getName(),
                            inputJson, cause.getClass().getSimpleName() + ": " + cause.getMessage(), duration);
                    logger.error("❌ ERROR: {}({}) - Unexpected Exception: {} - {}",
                            method.getName(), inputJson, cause.getClass().getSimpleName(), cause.getMessage());
                }
            }
        } catch (Exception e) {
            reportBuilder.addError(serviceInstance.getClass().getName(), method.getName(),
                    inputJson, e.getClass().getSimpleName() + ": " + e.getMessage(), 0);
            logger.error("❌ ERROR: {}({}) - Exception: {} - {}",
                    method.getName(), inputJson, e.getClass().getSimpleName(), e.getMessage());
        } finally {
            // Clear thread local
            currentMethodName.remove();
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
     * Parses multiple parameters from a JSON array string
     */
    private Object[] parseMultipleInputs(String inputJson, Class<?>[] paramTypes) throws JsonProcessingException {
        Object[] inputs = objectMapper.readValue(inputJson, Object[].class);
        Object[] methodParams = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            String paramJson = objectMapper.writeValueAsString(inputs[i]);
            methodParams[i] = parseInput(paramJson, paramTypes[i]);
        }

        return methodParams;
    }

    /**
     * Parses the expected output considering special cases
     */
    private Object parseExpectedOutput(String json, Class<?> targetType) throws JsonProcessingException {
        // Special handling for Optional
        if (Optional.class.isAssignableFrom(targetType)) {
            return json; // Return raw JSON string to handle expected format
        }

        return parseInput(json, targetType);
    }

    /**
     * Parses a single input parameter from JSON
     */
    private Object parseInput(String json, Class<?> targetType) throws JsonProcessingException {
        if (json == null || json.equals(NULL_PLACEHOLDER)) {
            return null;
        }

        // Special handling for collections of Product objects
        if (List.class.isAssignableFrom(targetType) && json.contains("\"name\"") && json.contains("\"price\"")) {
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, Product.class);
            return objectMapper.readValue(json, type);
        }

        // Handle special token replacements (Integer.MAX_VALUE, etc.)
        json = replaceSpecialTokens(json);

        return objectMapper.readValue(json, targetType);
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
     * Runs tests for a specific service class
     */
    public TestReport runTestsForClass(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (!clazz.isAnnotationPresent(Service.class)) {
                logger.warn("Class {} is not a Service", className);
                return reportBuilder.build();
            }

            // Create mocks first
            createAllMocks(clazz);

            Object serviceInstance = clazz.getDeclaredConstructor().newInstance();
            injectMocks(serviceInstance);

            runTestsForClass(serviceInstance);

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
        try {
            Class<?> clazz = Class.forName(className);
            if (!clazz.isAnnotationPresent(Service.class)) {
                logger.warn("Class {} is not a Service", className);
                return reportBuilder.build();
            }

            // Create mocks first
            createAllMocks(clazz);

            Object serviceInstance = clazz.getDeclaredConstructor().newInstance();
            injectMocks(serviceInstance);

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
     * Main method for running tests from command line
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            // Run all tests with default configuration
            UnitTestRunner runner = new UnitTestRunner("com.reflectiontest.springReflectionTest", false, true, false, 1);
            TestReport report = runner.runTests();
            System.out.println(report.getSummary());
        } else if (args.length == 1) {
            // Run tests for specific class
            UnitTestRunner runner = new UnitTestRunner("com.reflectiontest.springReflectionTest", false, true, false, 1);
            TestReport report = runner.runTestsForClass(args[0]);
            System.out.println(report.getSummary());
        } else if (args.length == 2) {
            // Run tests for specific method
            UnitTestRunner runner = new UnitTestRunner("com.reflectiontest.springReflectionTest", false, true, false, 1);
            TestReport report = runner.runTestsForMethod(args[0], args[1]);
            System.out.println(report.getSummary());
        } else {
            System.out.println("Usage: UnitTestRunner [className] [methodName]");
        }
    }
    /**
     * Helper class to handle ExecutorService resource management
     */
    private static class ExecutorServiceResource implements AutoCloseable {
        private final ExecutorService executor;

        public ExecutorServiceResource(int threads) {
            this.executor = Executors.newFixedThreadPool(threads);
        }

        public ExecutorService getExecutor() {
            return executor;
        }

        @Override
        public void close() {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
