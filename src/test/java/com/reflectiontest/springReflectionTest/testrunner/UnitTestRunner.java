package com.reflectiontest.springReflectionTest.testrunner;

import com.reflectiontest.springReflectionTest.annotations.ExpectedResult;
import com.reflectiontest.springReflectionTest.annotations.IntegrationTest;
import com.reflectiontest.springReflectionTest.reporting.TestReport;
import com.reflectiontest.springReflectionTest.testrunner.config.TestRunnerConfiguration;
import com.reflectiontest.springReflectionTest.testrunner.core.*;
import com.reflectiontest.springReflectionTest.testrunner.util.InputParser;
import com.reflectiontest.springReflectionTest.testrunner.util.JsonComparator;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Central unit test runner that coordinates test execution
 */
public class UnitTestRunner {
    private static final Logger logger = LoggerFactory.getLogger(UnitTestRunner.class);

    // Core components
    private final MockConfigurationManager mockManager;
    private final TestReportManager reportManager;
    private final BytecodeVerificationService bytecodeVerifier;
    private final InputParser inputParser;
    private final JsonComparator jsonComparator;

    // Configuration
    private final TestRunnerConfiguration configuration;

    /**
     * Constructor with default configuration
     */
    public UnitTestRunner() {
        this(TestRunnerConfiguration.configure().build());
    }

    /**
     * Constructor with custom configuration
     * @param configuration Test runner configuration
     */
    public UnitTestRunner(TestRunnerConfiguration configuration) {
        this.configuration = configuration;
        this.mockManager = new MockConfigurationManager();
        this.reportManager = new TestReportManager()
                .setMetricsCollection(configuration.isCollectMetrics())
                .setDetailedLogging(true);
        this.bytecodeVerifier = new BytecodeVerificationService();
        this.inputParser = new InputParser();
        this.jsonComparator = new JsonComparator();
    }

    /**
     * Runs all unit tests in the configured base package
     * @return TestReport containing test results
     */
    public TestReport runTests() {
        logger.info("🔍 Starting unit test run in package: {}", configuration.getBasePackage());

        // Scan for service classes
        Reflections reflections = new Reflections(configuration.getBasePackage());
        Set<Class<?>> serviceClasses = reflections.getTypesAnnotatedWith(Service.class);

        logger.info("Found {} service classes to test", serviceClasses.size());

        // Create and configure mocks
        mockManager.createAndConfigureMocks();

        // Run tests for each service class
        for (Class<?> clazz : serviceClasses) {
            try {
                Object serviceInstance = createServiceInstance(clazz);
                if (serviceInstance != null) {
                    runTestsForClass(serviceInstance);
                }
            } catch (Exception e) {
                logger.error("Error processing service class {}: {}", clazz.getName(), e.getMessage());
                reportManager.recordTestError(
                        null,
                        null,
                        clazz.getName(),
                        "Class initialization failed: " + e.getMessage(),
                        0
                );
            }
        }

        // Generate and return final report
        return reportManager.generateFinalReport();
    }

    /**
     * Creates a service instance with dependencies
     * @param clazz Service class to instantiate
     * @return Service instance
     */
    private Object createServiceInstance(Class<?> clazz) throws Exception {
        // Try constructor injection first
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            Class<?>[] paramTypes = constructor.getParameterTypes();
            if (paramTypes.length > 0) {
                Object[] args = new Object[paramTypes.length];
                boolean canInject = true;

                for (int i = 0; i < paramTypes.length; i++) {
                    Object mockInstance = mockManager.getMockForType(paramTypes[i]);
                    if (mockInstance == null) {
                        canInject = false;
                        break;
                    }
                    args[i] = mockInstance;
                }

                if (canInject) {
                    constructor.setAccessible(true);
                    return constructor.newInstance(args);
                }
            }
        }

        // Fallback to default constructor and field injection
        Object instance = clazz.getDeclaredConstructor().newInstance();
        mockManager.injectMocksIntoInstance(instance);
        return instance;
    }

    /**
     * Runs tests for a specific service class
     * @param serviceInstance Service instance to test
     */
    private void runTestsForClass(Object serviceInstance) {
        Class<?> clazz = serviceInstance.getClass();

        for (Method method : clazz.getDeclaredMethods()) {
            // Only run methods with both IntegrationTest and ExpectedResult annotations
            if (method.isAnnotationPresent(IntegrationTest.class)) {
                ExpectedResult[] testCases = method.getAnnotationsByType(ExpectedResult.class);
                if (testCases.length > 0) {
                    logger.info("Running tests for: {}.{}", clazz.getSimpleName(), method.getName());

                    // Verify bytecode if configured
                    if (configuration.isVerifyBytecodeHash()) {
                        bytecodeVerifier.verifyMethodBytecode(clazz, method);
                    }

                    // Run each test case
                    for (ExpectedResult testCase : testCases) {
                        runSingleTest(serviceInstance, method, testCase);
                    }
                }
            }
        }
    }

    /**
     * Runs a single test case
     * @param serviceInstance Service instance
     * @param method Method being tested
     * @param testCase Test case details
     */
    private void runSingleTest(Object serviceInstance, Method method, ExpectedResult testCase) {
        long startTime = System.nanoTime();

        try {
            // Prepare method parameters
            Class<?>[] paramTypes = method.getParameterTypes();
            Object[] methodParams = paramTypes.length == 1
                    ? new Object[]{ inputParser.parseInput(testCase.inputJson(), paramTypes[0]) }
                    : inputParser.parseMultipleInputs(testCase.inputJson(), paramTypes);

            // Invoke method
            Object actualOutput = method.invoke(serviceInstance, methodParams);
            long endTime = System.nanoTime();
            long executionTimeMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

            // Handle special case for expected exceptions
            if (testCase.expectedJson().equals("__THROWS__")) {
                reportManager.recordFailedTest(
                        serviceInstance,
                        method,
                        testCase.inputJson(),
                        "Expected exception",
                        "No exception thrown",
                        executionTimeMs
                );
                return;
            }

            // Parse expected output
            Object expectedOutput = inputParser.parseInput(testCase.expectedJson(), method.getReturnType());

            // Convert to JSON for comparison
            String expectedJson = expectedOutput == null ? "null" :
                    inputParser.createObjectMapper().writeValueAsString(expectedOutput);
            String actualJson = actualOutput == null ? "null" :
                    inputParser.createObjectMapper().writeValueAsString(actualOutput);

            // Compare results
            if (jsonComparator.compareJsonContents(expectedJson, actualJson)) {
                reportManager.recordSuccessfulTest(
                        serviceInstance,
                        method,
                        testCase.inputJson(),
                        expectedJson,
                        actualJson,
                        executionTimeMs
                );
            } else {
                reportManager.recordFailedTest(
                        serviceInstance,
                        method,
                        testCase.inputJson(),
                        expectedJson,
                        actualJson,
                        executionTimeMs
                );
            }

        } catch (Exception e) {
            long endTime = System.nanoTime();
            long executionTimeMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

            // Handle invocation exceptions
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException
                    ? ((java.lang.reflect.InvocationTargetException) e).getTargetException()
                    : e;

            // Check if exception was expected
            if (testCase.expectedJson().equals("__THROWS__")) {
                reportManager.recordSuccessfulTest(
                        serviceInstance,
                        method,
                        testCase.inputJson(),
                        "Exception expected",
                        "Exception: " + cause.getClass().getSimpleName(),
                        executionTimeMs
                );
            } else {
                reportManager.recordTestError(
                        serviceInstance,
                        method,
                        testCase.inputJson(),
                        cause.getClass().getSimpleName() + ": " + cause.getMessage(),
                        executionTimeMs
                );
            }
        }
    }

    /**
     * Main method for running tests from command line
     */
    public static void main(String[] args) {
        UnitTestRunner runner = new UnitTestRunner();
        TestReport report = runner.runTests();
        System.out.println(report.getSummary());
    }
}