package com.reflectiontest.springReflectionTest.annotations;

import java.lang.annotation.*;

/**
 * Annotation to define mock return values for repository methods
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(MockReturns.class)
public @interface MockReturn {
    /**
     * JSON representation of input parameters
     */
    String inputJson() default "";

    /**
     * JSON representation of the expected return value
     */
    String returnJson();

    /**
     * Optional flag to indicate whether this is the default return value
     * when no specific input matches
     */
    boolean isDefault() default false;
}
