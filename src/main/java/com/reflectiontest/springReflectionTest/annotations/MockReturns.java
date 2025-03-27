package com.reflectiontest.springReflectionTest.annotations;

import java.lang.annotation.*;

/**
 * Container annotation to allow multiple MockReturn annotations
 * on a single method
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MockReturns {
    MockReturn[] value();
}
