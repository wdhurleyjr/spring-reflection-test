package com.reflectiontest.springReflectionTest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reflectiontest.springReflectionTest.annotations.ExpectedResult;
import com.reflectiontest.springReflectionTest.annotations.MockDependency;
import com.reflectiontest.springReflectionTest.metrics.MetricsCollector;
import com.reflectiontest.springReflectionTest.reporting.TestReport;
import com.reflectiontest.springReflectionTest.reporting.TestReportBuilder;
import com.reflectiontest.springReflectionTest.reporting.TestStatus;
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
    private static final ObjectMapper objectMapper = new ObjectMapper();
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
     * Runs tests for all service classes in parallel
     */
    private TestReport runTestsInParallel(Set<Class<?>> serviceClasses) {
        logger.info("Running tests in parallel with {} threads", maxThreads);

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
     * Injects mock dependencies into a service instance
     */
    private void injectMocks(Object serviceInstance) throws IllegalAccessException {
        for (Field field : serviceInstance.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(MockDependency.class)) {
                field.setAccessible(true);

                Object mockInstance = mockInstances.get(field.getType());
                if (mockInstance == null) {
                    mockInstance = Mockito.mock(field.getType());
                    mockInstances.put(field.getType(), mockInstance);
                }

                field.set(serviceInstance, mockInstance);
                logger.debug("Injected mock for: {} in {}",
                        field.getType().getSimpleName(), serviceInstance.getClass().getSimpleName());
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
            // Reset mocks before each test to ensure independent executions
            for (Object mock : mockInstances.values()) {
                Mockito.reset(mock);
            }

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
                    Object expectedOutput = parseInput(expectedJsonValue, method.getReturnType());
                    String serializedExpected = objectMapper.writeValueAsString(expectedOutput);
                    String serializedActual = objectMapper.writeValueAsString(actualOutput);

                    if (serializedExpected.equals(serializedActual)) {
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
        }
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
     * Parses a single input parameter from JSON
     */
    private Object parseInput(String json, Class<?> targetType) throws JsonProcessingException {
        if (json == null || json.equals(NULL_PLACEHOLDER)) {
            return null;
        }
        return objectMapper.readValue(json, targetType);
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
            UnitTestRunner runner = new UnitTestRunner();
            TestReport report = runner.runTests();
            System.out.println(report.getSummary());
        } else if (args.length == 1) {
            // Run tests for specific class
            UnitTestRunner runner = new UnitTestRunner();
            TestReport report = runner.runTestsForClass(args[0]);
            System.out.println(report.getSummary());
        } else if (args.length == 2) {
            // Run tests for specific method
            UnitTestRunner runner = new UnitTestRunner();
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