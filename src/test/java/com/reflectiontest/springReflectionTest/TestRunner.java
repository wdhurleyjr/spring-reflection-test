package com.reflectiontest.springReflectionTest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reflectiontest.springReflectionTest.annotations.ExpectedResult;
import com.reflectiontest.springReflectionTest.annotations.MockDependency;
import org.mockito.Mockito;
import org.reflections.Reflections;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TestRunner {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Map<Class<?>, Object> mockInstances = new HashMap<>();
    private static final String NULL_PLACEHOLDER = "__NULL__";

    public static void main(String[] args) throws Exception {
        // Automatically detect the base package
        String basePackage = detectBasePackage();
        System.out.println("🔍 Scanning package: " + basePackage);

        Reflections reflections = new Reflections(basePackage);

        for (Class<?> clazz : reflections.getTypesAnnotatedWith(Service.class)) {
            Object serviceInstance = clazz.getDeclaredConstructor().newInstance();
            injectMocks(serviceInstance);

            for (Method method : clazz.getDeclaredMethods()) {
                ExpectedResult[] testCases = method.getAnnotationsByType(ExpectedResult.class);
                if (testCases.length > 0) {
                    System.out.println("\n🔍 Running tests for: " + clazz.getSimpleName() + "." + method.getName());
                    Arrays.stream(testCases).forEach(testCase ->
                            runTest(serviceInstance, method, testCase.inputJson(), testCase.expectedJson()));
                }
            }
        }
    }

    private static void injectMocks(Object serviceInstance) throws IllegalAccessException {
        for (Field field : serviceInstance.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(MockDependency.class)) {
                field.setAccessible(true);
                Object mockInstance = Mockito.mock(field.getType());
                field.set(serviceInstance, mockInstance);
                mockInstances.put(field.getType(), mockInstance);
                System.out.println("🔹 Injected mock for: " + field.getType().getSimpleName() + " in " +
                        serviceInstance.getClass().getSimpleName());
            }
        }
    }

    private static void runTest(Object serviceInstance, Method method, String inputJson, String expectedJson) {
        try {
            // Reset mocks before each test to ensure independent executions
            for (Object mock : mockInstances.values()) {
                Mockito.reset(mock);
            }

            long startTime = System.nanoTime();
            Class<?>[] paramTypes = method.getParameterTypes();
            Object[] methodParams;

            // Handle multi-parameter vs. single-parameter methods
            if (paramTypes.length == 1) {
                methodParams = new Object[]{parseInput(inputJson, paramTypes[0])};
            } else {
                Object[] inputs = objectMapper.readValue(inputJson, Object[].class);
                methodParams = new Object[paramTypes.length];
                for (int i = 0; i < paramTypes.length; i++) {
                    methodParams[i] = parseInput(objectMapper.writeValueAsString(inputs[i]), paramTypes[i]);
                }
            }

            // Check if test expects an exception
            boolean expectsException = "__THROWS__".equals(expectedJson);

            try {
                Object actualOutput = method.invoke(serviceInstance, methodParams);
                long endTime = System.nanoTime();
                long duration = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

                if (expectsException) {
                    System.err.println("❌ FAILED: " + method.getName() + "(" + inputJson + ")");
                    System.err.println("   - Expected: Exception (but method completed normally)");
                    System.err.println("   - Execution Time: " + duration + " ms");
                    return;
                }

                // Deserialize expected output normally
                Object expectedOutput = parseInput(expectedJson, method.getReturnType());
                boolean testPassed = objectMapper.writeValueAsString(actualOutput)
                        .equals(objectMapper.writeValueAsString(expectedOutput));

                if (testPassed) {
                    System.out.println("✅ PASSED: " + method.getName() + "(" + inputJson + ") -> " + actualOutput +
                            " [Execution Time: " + duration + " ms]");
                } else {
                    System.err.println("❌ FAILED: " + method.getName() + "(" + inputJson + ")");
                    System.err.println("   - Expected: " + expectedOutput);
                    System.err.println("   - Actual:   " + actualOutput);
                    System.err.println("   - Execution Time: " + duration + " ms");
                }
            } catch (InvocationTargetException e) {
                Throwable cause = e.getTargetException();
                long endTime = System.nanoTime();
                long duration = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

                if (expectsException) {
                    System.out.println("✅ PASSED: " + method.getName() + "(" + inputJson + ") -> Exception: " + cause.getClass().getSimpleName() +
                            " [Execution Time: " + duration + " ms]");
                } else {
                    System.err.println("❌ ERROR: " + method.getName() + " with input: " + inputJson);
                    System.err.println("   - Unexpected Exception: " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
                    cause.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: " + method.getName() + " with input: " + inputJson);
            System.err.println("   - Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Automatically detects the base package by checking the stack trace
    private static String detectBasePackage() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if (element.getClassName().startsWith("com.")) { // Adjust based on your org structure
                String className = element.getClassName();
                int lastDotIndex = className.lastIndexOf('.');
                return lastDotIndex > 0 ? className.substring(0, lastDotIndex) : className;
            }
        }
        return "com"; // Default to scanning all `com.` packages
    }

    // Parses JSON input and replaces "__NULL__" with `null` if needed
    private static Object parseInput(String json, Class<?> targetType) throws JsonProcessingException {
        if (json == null || json.equals(NULL_PLACEHOLDER)) {
            return null;
        }
        return objectMapper.readValue(json, targetType);
    }
}






