package com.reflectiontest.springReflectionTest.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to provide test object creation metadata for service constructors.
 * This information is used by the reflection-based testing framework to automatically
 * create and populate test objects needed for service testing.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.CONSTRUCTOR)
public @interface TestObjectCreation {
    /**
     * Defines the test objects needed for this service.
     */
    TestObject[] objects() default {};
}
