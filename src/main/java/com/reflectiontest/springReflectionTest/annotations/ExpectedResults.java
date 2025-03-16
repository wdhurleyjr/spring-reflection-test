package com.reflectiontest.springReflectionTest.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ExpectedResults {
    ExpectedResult[] value();
}
