package com.reflectiontest.springReflectionTest.testrunner.config;

/**
 * Configuration settings for the test runner
 */
public class TestRunnerConfiguration {
    // Configuration properties
    private boolean verifyBytecodeHash = true;
    private boolean collectMetrics = true;
    private String basePackage = "com.reflectiontest.springReflectionTest";

    // Private constructor to enforce builder pattern
    private TestRunnerConfiguration() {}

    // Getters
    public boolean isVerifyBytecodeHash() {
        return verifyBytecodeHash;
    }

    public boolean isCollectMetrics() {
        return collectMetrics;
    }

    public String getBasePackage() {
        return basePackage;
    }

    // Builder static inner class
    public static class Builder {
        private TestRunnerConfiguration config = new TestRunnerConfiguration();

        public Builder verifyBytecodeHash(boolean verify) {
            config.verifyBytecodeHash = verify;
            return this;
        }

        public Builder collectMetrics(boolean collect) {
            config.collectMetrics = collect;
            return this;
        }

        public Builder basePackage(String packageName) {
            config.basePackage = packageName;
            return this;
        }

        public TestRunnerConfiguration build() {
            return config;
        }
    }

    // Static method to start building configuration
    public static Builder configure() {
        return new Builder();
    }
}
