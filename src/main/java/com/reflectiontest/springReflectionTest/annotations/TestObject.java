package com.reflectiontest.springReflectionTest.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Defines a test object for service testing.
 * This annotation is used within {@link TestObjectCreation} to specify
 * the details of test objects needed for testing a service.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface TestObject {
    /**
     * The name of the test object, used for referencing in tests.
     */
    String name();

    /**
     * The class type of the test object.
     */
    Class<?> type();

    /**
     * JSON representation of the test object.
     */
    String json();

    /**
     * Field values for the test object as key-value pairs.
     * Format: "fieldName:value"
     */
    String[] fields() default {};

    /**
     * Whether this object should be used as a default/primary test object.
     */
    boolean isPrimary() default false;
}
