package com.reflectiontest.springReflectionTest.reporting;

/**
 * Base class for test result status
 */
public enum TestStatus {
    SUCCESS("✅ SUCCESS"),
    FAILURE("❌ FAILURE"),
    ERROR("⚠️ ERROR"),
    SKIPPED("⏭️ SKIPPED");

    private final String label;

    TestStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
