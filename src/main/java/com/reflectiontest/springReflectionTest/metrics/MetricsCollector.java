package com.reflectiontest.springReflectionTest.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects and aggregates performance metrics for test execution
 */
public class MetricsCollector {
    private final long startTime;
    private final AtomicLong totalExecutionTime = new AtomicLong(0);
    private final AtomicInteger totalTestsRun = new AtomicInteger(0);
    private final AtomicInteger totalPassed = new AtomicInteger(0);
    private final AtomicInteger totalFailed = new AtomicInteger(0);
    private final AtomicInteger totalErrors = new AtomicInteger(0);

    private final Map<String, AtomicLong> methodExecutionTimes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> methodExecutionCounts = new ConcurrentHashMap<>();

    public MetricsCollector() {
        this.startTime = System.nanoTime();
    }

    /**
     * Records a test execution with its duration
     */
    public void recordTestExecution(String methodId, long durationMs) {
        totalTestsRun.incrementAndGet();

        // Track per-method metrics
        methodExecutionTimes.computeIfAbsent(methodId, k -> new AtomicLong(0))
                .addAndGet(durationMs);
        methodExecutionCounts.computeIfAbsent(methodId, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    /**
     * Records a passed test
     */
    public void recordTestPassed() {
        totalPassed.incrementAndGet();
    }

    /**
     * Records a failed test
     */
    public void recordTestFailed() {
        totalFailed.incrementAndGet();
    }

    /**
     * Records a test error
     */
    public void recordTestError() {
        totalErrors.incrementAndGet();
    }

    /**
     * Records the total execution time
     */
    public void recordTotalExecutionTime(long nanoTime) {
        totalExecutionTime.set(nanoTime);
    }

    /**
     * Gets the start time of the metrics collection
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Gets all collected metrics as a map
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // Summary metrics
        metrics.put("totalExecutionTimeMs", totalExecutionTime.get() / 1_000_000);
        metrics.put("totalTestsRun", totalTestsRun.get());
        metrics.put("totalPassed", totalPassed.get());
        metrics.put("totalFailed", totalFailed.get());
        metrics.put("totalErrors", totalErrors.get());

        // Calculate success rate
        int total = totalPassed.get() + totalFailed.get() + totalErrors.get();
        double successRate = total > 0 ? (double) totalPassed.get() / total * 100 : 0;
        metrics.put("successRate", Math.round(successRate * 100) / 100.0);

        // Per-method metrics
        Map<String, Object> methodMetrics = new HashMap<>();
        for (Map.Entry<String, AtomicLong> entry : methodExecutionTimes.entrySet()) {
            String methodId = entry.getKey();
            long totalTime = entry.getValue().get();
            int count = methodExecutionCounts.get(methodId).get();

            Map<String, Object> methodData = new HashMap<>();
            methodData.put("totalTimeMs", totalTime);
            methodData.put("executionCount", count);
            methodData.put("averageTimeMs", count > 0 ? totalTime / count : 0);

            methodMetrics.put(methodId, methodData);
        }
        metrics.put("methods", methodMetrics);

        return metrics;
    }
}
