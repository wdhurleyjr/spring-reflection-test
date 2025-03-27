package com.reflectiontest.springReflectionTest.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Represents a complete test report with all test results
 */
public class TestReport {
    private static final Logger logger = LoggerFactory.getLogger(TestReport.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final String id;
    private final List<TestResult> results;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Map<String, Object> metrics;
    private final List<String> warnings;
    private final Map<String, Object> metadata;

    private TestReport(Builder builder) {
        this.id = builder.id;
        this.results = builder.results;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.metrics = builder.metrics;
        this.warnings = builder.warnings;
        this.metadata = builder.metadata;
    }

    public String getId() {
        return id;
    }

    public List<TestResult> getResults() {
        return results;
    }

    public List<TestResult> getResultsByStatus(TestStatus status) {
        return results.stream()
                .filter(r -> r.getStatus() == status)
                .collect(Collectors.toList());
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Gets a summary of the test report
     */
    public String getSummary() {
        int total = results.size();
        int passed = getResultsByStatus(TestStatus.SUCCESS).size();
        int failed = getResultsByStatus(TestStatus.FAILURE).size();
        int errors = getResultsByStatus(TestStatus.ERROR).size();
        int skipped = getResultsByStatus(TestStatus.SKIPPED).size();

        StringBuilder summary = new StringBuilder();
        summary.append("\n===================================================\n");
        summary.append("TEST REPORT SUMMARY - ").append(id).append("\n");
        summary.append("===================================================\n");
        summary.append(String.format("Start: %s\n", formatDateTime(startTime)));
        summary.append(String.format("End:   %s\n", formatDateTime(endTime)));
        summary.append(String.format("Duration: %.2f seconds\n",
                getDurationInSeconds()));
        summary.append("---------------------------------------------------\n");
        summary.append(String.format("Total Tests: %d\n", total));
        summary.append(String.format("Passed:      %d (%.1f%%)\n",
                passed, total > 0 ? (double) passed / total * 100 : 0));
        summary.append(String.format("Failed:      %d\n", failed));
        summary.append(String.format("Errors:      %d\n", errors));
        summary.append(String.format("Skipped:     %d\n", skipped));

        // Add performance metrics if available
        if (metrics != null && !metrics.isEmpty()) {
            summary.append("---------------------------------------------------\n");
            summary.append("PERFORMANCE METRICS\n");

            if (metrics.containsKey("averageExecutionTimeMs")) {
                summary.append(String.format("Average Test Time: %.2f ms\n",
                        metrics.get("averageExecutionTimeMs")));
            }

            if (metrics.containsKey("fastestTestMs") && metrics.containsKey("slowestTestMs")) {
                summary.append(String.format("Test Time Range: %d - %d ms\n",
                        metrics.get("fastestTestMs"),
                        metrics.get("slowestTestMs")));
            }
        }

        // Add warnings if there are any
        if (warnings != null && !warnings.isEmpty()) {
            summary.append("---------------------------------------------------\n");
            summary.append("WARNINGS\n");
            for (String warning : warnings) {
                summary.append("- ").append(warning).append("\n");
            }
        }

        // Add failed tests
        if (failed > 0) {
            summary.append("---------------------------------------------------\n");
            summary.append("FAILED TESTS\n");
            for (TestResult result : getResultsByStatus(TestStatus.FAILURE)) {
                summary.append(String.format("- %s.%s(%s)\n",
                        result.getClassName(),
                        result.getMethodName(),
                        result.getInput()));
                summary.append(String.format("  Expected: %s\n", result.getExpected()));
                summary.append(String.format("  Actual:   %s\n", result.getActual()));
                summary.append(String.format("  Time:     %d ms\n", result.getExecutionTimeMs()));
                summary.append("\n");
            }
        }

        // Add errors
        if (errors > 0) {
            summary.append("---------------------------------------------------\n");
            summary.append("ERRORS\n");
            for (TestResult result : getResultsByStatus(TestStatus.ERROR)) {
                summary.append(String.format("- %s.%s(%s)\n",
                        result.getClassName(),
                        result.getMethodName(),
                        result.getInput()));
                summary.append(String.format("  Error: %s\n", result.getErrorMessage()));
                summary.append(String.format("  Time:  %d ms\n", result.getExecutionTimeMs()));
                summary.append("\n");
            }
        }

        summary.append("===================================================\n");

        return summary.toString();
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "N/A";
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private double getDurationInSeconds() {
        if (startTime == null || endTime == null) return 0;
        return (endTime.toInstant(java.time.ZoneOffset.UTC).toEpochMilli() -
                startTime.toInstant(java.time.ZoneOffset.UTC).toEpochMilli()) / 1000.0;
    }

    /**
     * Saves the report to a JSON file
     */
    public boolean saveToFile(String filePath) {
        try {
            File file = new File(filePath);
            file.getParentFile().mkdirs();

            Map<String, Object> reportMap = toMap();
            objectMapper.writeValue(file, reportMap);

            logger.info("Report saved to: {}", filePath);
            return true;
        } catch (IOException e) {
            logger.error("Error saving report to file: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Loads a report from a JSON file
     */
    public static TestReport loadFromFile(String filePath) {
        try {
            String jsonContent = Files.readString(Paths.get(filePath));
            return fromJson(jsonContent);
        } catch (IOException e) {
            logger.error("Error loading report from file: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Converts the report to JSON
     */
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(toMap());
        } catch (Exception e) {
            logger.error("Error converting report to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Creates a report from JSON
     */
    public static TestReport fromJson(String json) {
        try {
            Map<String, Object> reportMap = objectMapper.readValue(json, Map.class);

            Builder builder = new Builder()
                    .id((String) reportMap.get("id"))
                    .startTime(parseDateTime((String) reportMap.get("startTime")))
                    .endTime(parseDateTime((String) reportMap.get("endTime")))
                    .metrics((Map<String, Object>) reportMap.get("metrics"))
                    .warnings((List<String>) reportMap.get("warnings"))
                    .metadata((Map<String, Object>) reportMap.get("metadata"));

            List<Map<String, Object>> resultMaps = (List<Map<String, Object>>) reportMap.get("results");
            if (resultMaps != null) {
                for (Map<String, Object> resultMap : resultMaps) {
                    TestResult.Builder resultBuilder = new TestResult.Builder()
                            .id((String) resultMap.get("id"))
                            .className((String) resultMap.get("className"))
                            .methodName((String) resultMap.get("methodName"))
                            .input((String) resultMap.get("input"))
                            .expected((String) resultMap.get("expected"))
                            .actual((String) resultMap.get("actual"))
                            .status(TestStatus.valueOf((String) resultMap.get("status")))
                            .executionTimeMs(((Number) resultMap.get("executionTimeMs")).longValue())
                            .errorMessage((String) resultMap.get("errorMessage"))
                            .timestamp(parseDateTime((String) resultMap.get("timestamp")));

                    Map<String, Object> metadataMap = (Map<String, Object>) resultMap.get("metadata");
                    if (metadataMap != null) {
                        resultBuilder.metadata(metadataMap);
                    }

                    builder.addResult(resultBuilder.build());
                }
            }

            return builder.build();

        } catch (Exception e) {
            logger.error("Error parsing report from JSON: {}", e.getMessage());
            return null;
        }
    }

    private static LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null) return null;
        try {
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            try {
                // Try with another common format
                return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception ex) {
                logger.error("Error parsing date time: {}", dateTimeStr);
                return null;
            }
        }
    }

    /**
     * Converts the report to a Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("startTime", startTime != null ? startTime.format(DateTimeFormatter.ISO_DATE_TIME) : null);
        map.put("endTime", endTime != null ? endTime.format(DateTimeFormatter.ISO_DATE_TIME) : null);
        map.put("metrics", metrics);
        map.put("warnings", warnings);
        map.put("metadata", metadata);

        List<Map<String, Object>> resultMaps = results.stream()
                .map(TestResult::toMap)
                .collect(Collectors.toList());

        map.put("results", resultMaps);

        return map;
    }

    public static class Builder {
        private String id;
        private List<TestResult> results = new ArrayList<>();
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Map<String, Object> metrics = new HashMap<>();
        private List<String> warnings = new ArrayList<>();
        private Map<String, Object> metadata = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder addResult(TestResult result) {
            this.results.add(result);
            return this;
        }

        public Builder results(List<TestResult> results) {
            this.results = new ArrayList<>(results);
            return this;
        }

        public Builder startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(LocalDateTime endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder metrics(Map<String, Object> metrics) {
            if (metrics != null) {
                this.metrics = new HashMap<>(metrics);
            }
            return this;
        }

        public Builder addMetric(String key, Object value) {
            this.metrics.put(key, value);
            return this;
        }

        public Builder warnings(List<String> warnings) {
            if (warnings != null) {
                this.warnings = new ArrayList<>(warnings);
            }
            return this;
        }

        public Builder addWarning(String warning) {
            this.warnings.add(warning);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            if (metadata != null) {
                this.metadata = new HashMap<>(metadata);
            }
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public TestReport build() {
            // Generate ID if not provided
            if (id == null) {
                this.id = UUID.randomUUID().toString();
            }

            // Set default times if not provided
            if (startTime == null) {
                startTime = LocalDateTime.now().minusSeconds(1);
            }

            if (endTime == null) {
                endTime = LocalDateTime.now();
            }

            // Calculate some metrics if not provided
            if (!metrics.containsKey("totalTests")) {
                metrics.put("totalTests", results.size());
            }

            if (!metrics.containsKey("passedTests")) {
                long passedCount = results.stream()
                        .filter(r -> r.getStatus() == TestStatus.SUCCESS)
                        .count();
                metrics.put("passedTests", passedCount);
            }

            if (!metrics.containsKey("failedTests")) {
                long failedCount = results.stream()
                        .filter(r -> r.getStatus() == TestStatus.FAILURE)
                        .count();
                metrics.put("failedTests", failedCount);
            }

            if (!metrics.containsKey("errorTests")) {
                long errorCount = results.stream()
                        .filter(r -> r.getStatus() == TestStatus.ERROR)
                        .count();
                metrics.put("errorTests", errorCount);
            }

            if (!metrics.containsKey("skippedTests")) {
                long skippedCount = results.stream()
                        .filter(r -> r.getStatus() == TestStatus.SKIPPED)
                        .count();
                metrics.put("skippedTests", skippedCount);
            }

            if (!metrics.containsKey("averageExecutionTimeMs") && !results.isEmpty()) {
                double avgTime = results.stream()
                        .mapToLong(TestResult::getExecutionTimeMs)
                        .average()
                        .orElse(0);
                metrics.put("averageExecutionTimeMs", avgTime);
            }

            if (!metrics.containsKey("successRate") && !results.isEmpty()) {
                long passedCount = (long) metrics.get("passedTests");
                double successRate = (double) passedCount / results.size() * 100;
                metrics.put("successRate", successRate);
            }

            if (!metrics.containsKey("fastestTestMs") && !results.isEmpty()) {
                long fastestTime = results.stream()
                        .mapToLong(TestResult::getExecutionTimeMs)
                        .min()
                        .orElse(0);
                metrics.put("fastestTestMs", fastestTime);
            }

            if (!metrics.containsKey("slowestTestMs") && !results.isEmpty()) {
                long slowestTime = results.stream()
                        .mapToLong(TestResult::getExecutionTimeMs)
                        .max()
                        .orElse(0);
                metrics.put("slowestTestMs", slowestTime);
            }

            return new TestReport(this);
        }
    }
}
