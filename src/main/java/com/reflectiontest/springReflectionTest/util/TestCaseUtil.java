package com.reflectiontest.springReflectionTest.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.reflectiontest.springReflectionTest.annotations.ExpectedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility for managing test cases
 */
public class TestCaseUtil {
    private static final Logger logger = LoggerFactory.getLogger(TestCaseUtil.class);
    private static final ObjectMapper objectMapper = createObjectMapper();
    private static final String TEST_STORAGE_DIRECTORY = ".reflecttest/testcases";

    /**
     * Creates and configures the ObjectMapper with necessary modules and settings
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Add support for Java 8 features like Optional
        mapper.registerModule(new Jdk8Module());
        // Configure mapper for consistent serialization/deserialization
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return mapper;
    }

    /**
     * Generates a unique ID for a test case
     */
    public static String generateTestCaseId(String methodId, String inputJson, String expectedJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Combine method ID, input, and expected output to create a unique hash
            String combinedData = methodId + "|" + inputJson + "|" + expectedJson;
            byte[] hash = digest.digest(combinedData.getBytes());

            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            logger.error("Error generating test case ID: {}", e.getMessage());
            // Fallback to a less ideal but functional approach
            return methodId.hashCode() + "-" + inputJson.hashCode() + "-" + expectedJson.hashCode();
        }
    }

    /**
     * Saves a test case to storage
     */
    public static boolean saveTestCase(String methodId, String inputJson, String expectedJson, boolean aiGenerated) {
        String testCaseId = generateTestCaseId(methodId, inputJson, expectedJson);
        String filePath = getTestCaseFilePath(testCaseId);

        // Using LinkedHashMap for consistent key order in serialization
        Map<String, Object> testCaseData = new LinkedHashMap<>();
        testCaseData.put("methodId", methodId);
        testCaseData.put("inputJson", inputJson);
        testCaseData.put("expectedJson", expectedJson);
        testCaseData.put("aiGenerated", aiGenerated);
        testCaseData.put("createdAt", System.currentTimeMillis());

        try {
            // Create directories if they don't exist
            File testCaseFile = new File(filePath);
            testCaseFile.getParentFile().mkdirs();

            // Write the test case to the file
            String jsonData = objectMapper.writeValueAsString(testCaseData);
            Files.writeString(Paths.get(filePath), jsonData);

            return true;
        } catch (IOException e) {
            logger.error("Error saving test case: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Loads all test cases for a method
     */
    public static List<Map<String, Object>> loadTestCasesForMethod(String methodId) {
        List<Map<String, Object>> testCases = new ArrayList<>();
        File testCaseDir = new File(TEST_STORAGE_DIRECTORY);

        if (!testCaseDir.exists() || !testCaseDir.isDirectory()) {
            return testCases;
        }

        try {
            Files.walk(testCaseDir.toPath())
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            String jsonContent = Files.readString(path);
                            Map<String, Object> testCase = objectMapper.readValue(jsonContent, Map.class);

                            if (methodId.equals(testCase.get("methodId"))) {
                                testCases.add(testCase);
                            }
                        } catch (IOException e) {
                            logger.error("Error reading test case file {}: {}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            logger.error("Error walking test case directory: {}", e.getMessage());
        }

        return testCases;
    }

    /**
     * Gets the file path for storing a test case
     */
    private static String getTestCaseFilePath(String testCaseId) {
        return TEST_STORAGE_DIRECTORY + "/" + testCaseId + ".json";
    }

    /**
     * Generates test cases using AI for a method
     */
    public static List<Map<String, Object>> generateTestCasesWithAI(Class<?> clazz, Method method, int numCases) {
        // This is a stub that would integrate with your AI service
        logger.info("Generating {} test cases with AI for {}.{}",
                numCases, clazz.getSimpleName(), method.getName());

        // In a real implementation, this would call your AI service
        // For now, we'll return empty list
        return new ArrayList<>();
    }

    /**
     * Converts ExpectedResult annotations to test cases
     */
    public static List<Map<String, Object>> convertAnnotationsToTestCases(Class<?> clazz, Method method) {
        List<Map<String, Object>> testCases = new ArrayList<>();
        String methodId = clazz.getName() + "." + method.getName();

        ExpectedResult[] annotations = method.getAnnotationsByType(ExpectedResult.class);
        for (ExpectedResult annotation : annotations) {
            // Using LinkedHashMap for consistent key order in serialization
            Map<String, Object> testCase = new LinkedHashMap<>();
            testCase.put("methodId", methodId);
            testCase.put("inputJson", annotation.inputJson());
            testCase.put("expectedJson", annotation.expectedJson());
            testCase.put("aiGenerated", false);
            testCase.put("createdAt", System.currentTimeMillis());

            testCases.add(testCase);
        }

        return testCases;
    }

    /**
     * Exports test cases as annotation strings
     */
    public static List<String> exportTestCasesAsAnnotations(List<Map<String, Object>> testCases) {
        List<String> annotations = new ArrayList<>();

        for (Map<String, Object> testCase : testCases) {
            String inputJson = (String) testCase.get("inputJson");
            String expectedJson = (String) testCase.get("expectedJson");

            // Escape quotes for Java string literals
            inputJson = inputJson.replace("\"", "\\\"");
            expectedJson = expectedJson.replace("\"", "\\\"");

            String annotation = String.format("@ExpectedResult(inputJson = \"%s\", expectedJson = \"%s\")",
                    inputJson, expectedJson);

            annotations.add(annotation);
        }

        return annotations;
    }

    /**
     * Processes special token values in JSON input strings
     */
    public static String processSpecialTokens(String input) {
        if (input == null) {
            return null;
        }

        // Handle special tokens for test input/output
        if (input.contains("Integer.MAX_VALUE")) {
            input = input.replace("Integer.MAX_VALUE", String.valueOf(Integer.MAX_VALUE));
        }
        if (input.contains("Integer.MIN_VALUE")) {
            input = input.replace("Integer.MIN_VALUE", String.valueOf(Integer.MIN_VALUE));
        }

        return input;
    }
}