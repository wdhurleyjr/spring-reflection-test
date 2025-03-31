package com.reflectiontest.springReflectionTest.testrunner.core;

import com.reflectiontest.springReflectionTest.metrics.MetricsCollector;
import com.reflectiontest.springReflectionTest.reporting.TestReport;
import com.reflectiontest.springReflectionTest.reporting.TestReportBuilder;
import com.reflectiontest.springReflectionTest.reporting.TestResult;
import com.reflectiontest.springReflectionTest.reporting.TestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages test reporting and metrics collection
 */
public class TestReportManager {
    private static final Logger logger = LoggerFactory.getLogger(TestReportManager.class);

    // Components for building test reports
    private final MetricsCollector metricsCollector;
    private final TestReportBuilder reportBuilder;

    // Tracking test execution details
    private final List<TestExecutionRecord> testExecutionRecords;
    private final LocalDateTime startTime;

    // Configuration flags
    private boolean collectMetrics = true;
    private boolean includeDetailedLogs = true;

    public TestReportManager() {
        this.metricsCollector = new MetricsCollector();
        this.reportBuilder = new TestReportBuilder();
        this.testExecutionRecords = new ArrayList<>();
        this.startTime = LocalDateTime.now();
    }

    /**
     * Configuration method to toggle metrics collection
     * @param collectMetrics Whether to collect performance metrics
     * @return this TestReportManager for method chaining
     */
    public TestReportManager setMetricsCollection(boolean collectMetrics) {
        this.collectMetrics = collectMetrics;
        return this;
    }

    /**
     * Configuration method to toggle detailed logging
     * @param includeDetailedLogs Whether to include detailed test logs
     * @return this TestReportManager for method chaining
     */
    public TestReportManager setDetailedLogging(boolean includeDetailedLogs) {
        this.includeDetailedLogs = includeDetailedLogs;
        return this;
    }

    /**
     * Records a successful test execution
     */
    public void recordSuccessfulTest(
            Object serviceInstance,
            Method method,
            String inputJson,
            String expectedJson,
            String actualJson,
            long executionTimeMs
    ) {
        // Create test result
        TestResult testResult = createTestResult(
                serviceInstance,
                method,
                inputJson,
                expectedJson,
                actualJson,
                TestStatus.SUCCESS,
                executionTimeMs
        );

        // Add to report builder
        reportBuilder.addSuccess(
                serviceInstance.getClass().getName(),
                method.getName(),
                inputJson,
                expectedJson,
                actualJson,
                executionTimeMs
        );

        // Record metrics if enabled
        if (collectMetrics) {
            metricsCollector.recordTestExecution(
                    getMethodIdentifier(serviceInstance, method),
                    executionTimeMs
            );
            metricsCollector.recordTestPassed();
        }

        // Log detailed information if enabled
        if (includeDetailedLogs) {
            logger.info("✅ Test Passed: {}.{} [{}ms]",
                    serviceInstance.getClass().getSimpleName(),
                    method.getName(),
                    executionTimeMs
            );
        }

        // Store execution record
        testExecutionRecords.add(new TestExecutionRecord(
                serviceInstance.getClass().getName(),
                method.getName(),
                TestStatus.SUCCESS,
                executionTimeMs
        ));
    }

    /**
     * Records a failed test execution
     */
    public void recordFailedTest(
            Object serviceInstance,
            Method method,
            String inputJson,
            String expectedJson,
            String actualJson,
            long executionTimeMs
    ) {
        // Create test result
        TestResult testResult = createTestResult(
                serviceInstance,
                method,
                inputJson,
                expectedJson,
                actualJson,
                TestStatus.FAILURE,
                executionTimeMs
        );

        // Add to report builder
        reportBuilder.addFailure(
                serviceInstance.getClass().getName(),
                method.getName(),
                inputJson,
                expectedJson,
                actualJson,
                executionTimeMs
        );

        // Record metrics if enabled
        if (collectMetrics) {
            metricsCollector.recordTestExecution(
                    getMethodIdentifier(serviceInstance, method),
                    executionTimeMs
            );
            metricsCollector.recordTestFailed();
        }

        // Log detailed information if enabled
        if (includeDetailedLogs) {
            logger.error("❌ Test Failed: {}.{} [{}ms]",
                    serviceInstance.getClass().getSimpleName(),
                    method.getName(),
                    executionTimeMs
            );
        }

        // Store execution record
        testExecutionRecords.add(new TestExecutionRecord(
                serviceInstance.getClass().getName(),
                method.getName(),
                TestStatus.FAILURE,
                executionTimeMs
        ));
    }

    /**
     * Records a test error
     */
    public void recordTestError(
            Object serviceInstance,
            Method method,
            String inputJson,
            String errorMessage,
            long executionTimeMs
    ) {
        // Add to report builder
        reportBuilder.addError(
                serviceInstance.getClass().getName(),
                method.getName(),
                inputJson,
                errorMessage,
                executionTimeMs
        );

        // Record metrics if enabled
        if (collectMetrics) {
            metricsCollector.recordTestExecution(
                    getMethodIdentifier(serviceInstance, method),
                    executionTimeMs
            );
            metricsCollector.recordTestError();
        }

        // Log detailed information if enabled
        if (includeDetailedLogs) {
            logger.error("⚠️ Test Error: {}.{} - {} [{}ms]",
                    serviceInstance.getClass().getSimpleName(),
                    method.getName(),
                    errorMessage,
                    executionTimeMs
            );
        }

        // Store execution record
        testExecutionRecords.add(new TestExecutionRecord(
                serviceInstance.getClass().getName(),
                method.getName(),
                TestStatus.ERROR,
                executionTimeMs
        ));
    }

    /**
     * Generates the final test report
     */
    public TestReport generateFinalReport() {
        // Calculate total execution time
        long totalExecutionTime = System.nanoTime() - metricsCollector.getStartTime();

        // Add metrics to the report
        if (collectMetrics) {
            metricsCollector.recordTotalExecutionTime(totalExecutionTime);
            reportBuilder.setMetrics(metricsCollector.getMetrics());
        }

        // Add additional metadata
        reportBuilder.addMetadata("totalTests", testExecutionRecords.size());
        reportBuilder.addMetadata("startTime", startTime);

        // Generate and return the report
        return reportBuilder.build();
    }

    /**
     * Creates a unique identifier for a method
     */
    private String getMethodIdentifier(Object serviceInstance, Method method) {
        return serviceInstance.getClass().getName() + "." + method.getName();
    }

    /**
     * Creates a test result object
     */
    private TestResult createTestResult(
            Object serviceInstance,
            Method method,
            String inputJson,
            String expectedJson,
            String actualJson,
            TestStatus status,
            long executionTimeMs
    ) {
        return new TestResult.Builder()
                .className(serviceInstance.getClass().getName())
                .methodName(method.getName())
                .input(inputJson)
                .expected(expectedJson)
                .actual(actualJson)
                .status(status)
                .executionTimeMs(executionTimeMs)
                .build();
    }

    /**
     * Inner class to track test execution records
     */
    private static class TestExecutionRecord {
        final String className;
        final String methodName;
        final TestStatus status;
        final long executionTimeMs;

        TestExecutionRecord(String className, String methodName, TestStatus status, long executionTimeMs) {
            this.className = className;
            this.methodName = methodName;
            this.status = status;
            this.executionTimeMs = executionTimeMs;
        }
    }
}
