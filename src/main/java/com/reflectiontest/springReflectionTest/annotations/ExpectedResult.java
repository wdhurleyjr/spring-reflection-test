package com.reflectiontest.springReflectionTest.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(ExpectedResults.class)
public @interface ExpectedResult {
    String inputJson();  // JSON input for complex objects
    String expectedJson(); // JSON output for complex objects
}


