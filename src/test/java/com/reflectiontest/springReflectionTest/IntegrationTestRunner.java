package com.reflectiontest.springReflectionTest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reflectiontest.springReflectionTest.annotations.ExpectedResult;
import com.reflectiontest.springReflectionTest.annotations.IntegrationTest;
import com.reflectiontest.springReflectionTest.annotations.MockDependency;
import com.reflectiontest.springReflectionTest.repositories.MongoDatabaseManager;
import org.mockito.Mockito;
import org.reflections.Reflections;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class IntegrationTestRunner {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Map<Class<?>, Object> mockInstances = new HashMap<>();
    private static final String NULL_PLACEHOLDER = "__NULL__";

    public static void main(String[] args) throws Exception {
        String basePackage = detectBasePackage();
        System.out.println("🔍 Scanning package: " + basePackage);

        Reflections reflections = new Reflections(basePackage);

        for (Class<?> clazz : reflections.getTypesAnnotatedWith(Service.class)) {
            setupDatabase(); // ✅ Start per-class
            Object serviceInstance = clazz.getDeclaredConstructor().newInstance();
            injectMocks(serviceInstance);

            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(IntegrationTest.class)) continue;

                ExpectedResult[] testCases = method.getAnnotationsByType(ExpectedResult.class);
                if (testCases.length > 0) {
                    System.out.println("\n🔍 Running integration tests for: " + clazz.getSimpleName() + "." + method.getName());
                    for (ExpectedResult testCase : testCases) {
                        runTest(serviceInstance, method, testCase.inputJson(), testCase.expectedJson());
                    }
                }
            }

            cleanupDatabase(); // ✅ Stop per-class
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
            for (Object mock : mockInstances.values()) {
                Mockito.reset(mock);
            }

            long startTime = System.nanoTime();
            Class<?>[] paramTypes = method.getParameterTypes();
            Object[] methodParams = (paramTypes.length == 1)
                    ? new Object[]{parseInput(inputJson, paramTypes[0])}
                    : parseMultipleInputs(inputJson, paramTypes);

            boolean expectsException = "__THROWS__".equals(expectedJson);

            try {
                Object actualOutput = method.invoke(serviceInstance, methodParams);
                long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

                if (expectsException) {
                    System.err.println("❌ FAILED: Expected exception but method completed");
                    return;
                }

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
                }
            } catch (InvocationTargetException e) {
                Throwable cause = e.getTargetException();
                long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
                if (expectsException) {
                    System.out.println("✅ PASSED: Exception thrown as expected: " + cause.getClass().getSimpleName());
                } else {
                    System.err.println("❌ ERROR: Unexpected exception - " + cause);
                    cause.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR during test execution: " + e);
            e.printStackTrace();
        }
    }

    private static Object[] parseMultipleInputs(String inputJson, Class<?>[] paramTypes) throws JsonProcessingException {
        Object[] inputs = objectMapper.readValue(inputJson, Object[].class);
        Object[] methodParams = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            methodParams[i] = parseInput(objectMapper.writeValueAsString(inputs[i]), paramTypes[i]);
        }
        return methodParams;
    }

    private static Object parseInput(String json, Class<?> targetType) throws JsonProcessingException {
        if (json == null || json.equals(NULL_PLACEHOLDER)) return null;
        return objectMapper.readValue(json, targetType);
    }

    private static String detectBasePackage() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if (element.getClassName().startsWith("com.")) {
                String className = element.getClassName();
                int lastDotIndex = className.lastIndexOf('.');
                return lastDotIndex > 0 ? className.substring(0, lastDotIndex) : className;
            }
        }
        return "com";
    }

    // ✅ Integrated MongoDB Setup and Cleanup (per class now)
    private static void setupDatabase() {
        System.out.println("⚙️ Setting up MongoDB test instance...");
        MongoDatabaseManager.start();
    }

    private static void cleanupDatabase() {
        System.out.println("🧹 Cleaning up MongoDB test instance...");
        MongoDatabaseManager.stop();
    }
}



