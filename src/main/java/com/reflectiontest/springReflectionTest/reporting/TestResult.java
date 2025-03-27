package com.reflectiontest.springReflectionTest.reporting;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a single test execution result
 */
public class TestResult {
    private final String id;
    private final String className;
    private final String methodName;
    private final String input;
    private final String expected;
    private final String actual;
    private final TestStatus status;
    private final long executionTimeMs;
    private final String errorMessage;
    private final LocalDateTime timestamp;
    private final Map<String, Object> metadata;

    private TestResult(Builder builder) {
        this.id = builder.id;
        this.className = builder.className;
        this.methodName = builder.methodName;
        this.input = builder.input;
        this.expected = builder.expected;
        this.actual = builder.actual;
        this.status = builder.status;
        this.executionTimeMs = builder.executionTimeMs;
        this.errorMessage = builder.errorMessage;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.metadata = builder.metadata;
    }

    public String getId() {
        return id;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getInput() {
        return input;
    }

    public String getExpected() {
        return expected;
    }

    public String getActual() {
        return actual;
    }

    public TestStatus getStatus() {
        return status;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("className", className);
        map.put("methodName", methodName);
        map.put("input", input);
        map.put("expected", expected);
        map.put("actual", actual);
        map.put("status", status.name());
        map.put("executionTimeMs", executionTimeMs);
        map.put("errorMessage", errorMessage);
        map.put("timestamp", timestamp.format(DateTimeFormatter.ISO_DATE_TIME));
        map.put("metadata", metadata);
        return map;
    }

    public static class Builder {
        private String id;
        private String className;
        private String methodName;
        private String input;
        private String expected;
        private String actual;
        private TestStatus status;
        private long executionTimeMs;
        private String errorMessage;
        private LocalDateTime timestamp;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        public Builder input(String input) {
            this.input = input;
            return this;
        }

        public Builder expected(String expected) {
            this.expected = expected;
            return this;
        }

        public Builder actual(String actual) {
            this.actual = actual;
            return this;
        }

        public Builder status(TestStatus status) {
            this.status = status;
            return this;
        }

        public Builder executionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }

        public TestResult build() {
            // Generate ID if not provided
            if (id == null) {
                this.id = UUID.randomUUID().toString();
            }

            return new TestResult(this);
        }
    }
}
