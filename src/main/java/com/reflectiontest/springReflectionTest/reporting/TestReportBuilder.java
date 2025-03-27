package com.reflectiontest.springReflectionTest.reporting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds test reports incrementally during test execution
 */
public class TestReportBuilder {
    private static final Logger logger = LoggerFactory.getLogger(TestReportBuilder.class);

    private final String id;
    private final List<TestResult> results = new ArrayList<>();
    private final LocalDateTime startTime;
    private LocalDateTime endTime;
    private final Map<String, Object> metrics = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, Object> metadata = new HashMap<>();

    public TestReportBuilder() {
        this.id = UUID.randomUUID().toString();
        this.startTime = LocalDateTime.now();
    }

    public TestReportBuilder(String id) {
        this.id = id;
        this.startTime = LocalDateTime.now();
    }

    /**
     * Adds a successful test result
     */
    public void addSuccess(String className, String methodName, String input,
                           String expected, String actual, long executionTimeMs) {
        TestResult result = new TestResult.Builder()
                .className(className)
                .methodName(methodName)
                .input(input)
                .expected(expected)
                .actual(actual)
                .status(TestStatus.SUCCESS)
                .executionTimeMs(executionTimeMs)
                .build();

        results.add(result);
        logger.debug("Added success result for {}.{}", className, methodName);
    }

    /**
     * Adds a failed test result
     */
    public void addFailure(String className, String methodName, String input,
                           String expected, String actual, long executionTimeMs) {
        TestResult result = new TestResult.Builder()
                .className(className)
                .methodName(methodName)
                .input(input)
                .expected(expected)
                .actual(actual)
                .status(TestStatus.FAILURE)
                .executionTimeMs(executionTimeMs)
                .build();

        results.add(result);
        logger.debug("Added failure result for {}.{}", className, methodName);
    }

    /**
     * Adds a test error
     */
    public void addError(String className, String methodName, String input,
                         String errorMessage, long executionTimeMs) {
        TestResult result = new TestResult.Builder()
                .className(className)
                .methodName(methodName)
                .input(input)
                .status(TestStatus.ERROR)
                .errorMessage(errorMessage)
                .executionTimeMs(executionTimeMs)
                .build();

        results.add(result);
        logger.debug("Added error result for {}.{}", className, methodName);
    }

    /**
     * Adds a general error not tied to a specific test case
     */
    public void addError(String className, String errorMessage, Exception e) {
        String fullMessage = errorMessage;
        if (e != null) {
            fullMessage += ": " + e.getMessage();
        }

        TestResult result = new TestResult.Builder()
                .className(className)
                .methodName("initialization")
                .input("N/A")
                .status(TestStatus.ERROR)
                .errorMessage(fullMessage)
                .executionTimeMs(0)
                .build();

        results.add(result);
        logger.debug("Added error for class {}: {}", className, errorMessage);
    }

    /**
     * Adds a skipped test
     */
    public void addSkipped(String className, String methodName, String input, String reason) {
        TestResult result = new TestResult.Builder()
                .className(className)
                .methodName(methodName)
                .input(input)
                .status(TestStatus.SKIPPED)
                .errorMessage(reason)
                .executionTimeMs(0)
                .build();

        results.add(result);
        logger.debug("Added skipped result for {}.{}: {}", className, methodName, reason);
    }

    /**
     * Adds a warning to the report
     */
    public void addWarning(String className, String methodName, String message) {
        String warning = String.format("[%s.%s] %s", className, methodName, message);
        warnings.add(warning);
        logger.debug("Added warning: {}", warning);
    }

    /**
     * Sets the metrics for the report
     */
    public void setMetrics(Map<String, Object> metrics) {
        if (metrics != null) {
            this.metrics.putAll(metrics);
        }
    }

    /**
     * Adds a metric to the report
     */
    public void addMetric(String key, Object value) {
        metrics.put(key, value);
    }

    /**
     * Adds metadata to the report
     */
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * Builds the final test report
     */
    public TestReport build() {
        endTime = LocalDateTime.now();

        return new TestReport.Builder()
                .id(id)
                .results(results)
                .startTime(startTime)
                .endTime(endTime)
                .metrics(metrics)
                .warnings(warnings)
                .metadata(metadata)
                .build();
    }
}
