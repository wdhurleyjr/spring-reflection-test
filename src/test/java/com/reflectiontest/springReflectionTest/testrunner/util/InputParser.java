package com.reflectiontest.springReflectionTest.testrunner.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.reflectiontest.springReflectionTest.models.Product;
import com.reflectiontest.springReflectionTest.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

/**
 * Utility class for parsing input JSON to specific types
 */
public class InputParser {
    private static final Logger logger = LoggerFactory.getLogger(InputParser.class);
    private static final String NULL_PLACEHOLDER = "__NULL__";

    private final ObjectMapper objectMapper;

    public InputParser() {
        this.objectMapper = createObjectMapper();
    }

    /**
     * Creates a configured ObjectMapper with necessary modules and settings
     * @return Configured ObjectMapper
     */
    public ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return mapper;
    }

    /**
     * Parses input for a single parameter
     * @param json JSON input string
     * @param targetType Target parameter type
     * @return Parsed input object
     */
    public Object parseInput(String json, Class<?> targetType) throws JsonProcessingException {
        // Handle null or placeholder
        if (json == null || json.equals(NULL_PLACEHOLDER)) {
            return null;
        }

        // Replace special tokens
        json = replaceSpecialTokens(json);

        // Handle primitive types
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (json.equals("true") || json.equals("\"true\"")) return true;
            if (json.equals("false") || json.equals("\"false\"")) return false;
        }

        if (targetType == int.class || targetType == Integer.class) {
            try {
                return Integer.parseInt(json.replace("\"", ""));
            } catch (NumberFormatException e) {
                // Continue with normal parsing if not a simple integer
            }
        }

        // Handle specific model classes
        if (targetType == User.class) {
            try {
                return objectMapper.readValue(json, User.class);
            } catch (Exception e) {
                logger.warn("Failed to parse User from JSON: {}", e.getMessage());
            }
        }

        if (targetType == Product.class) {
            try {
                return objectMapper.readValue(json, Product.class);
            } catch (Exception e) {
                logger.warn("Failed to parse Product from JSON: {}", e.getMessage());
            }
        }

        // Special handling for collections with generic types
        if (Collection.class.isAssignableFrom(targetType) && json.startsWith("[")) {
            // Try to infer the element type from the JSON content
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(
                    List.class, inferElementType(json)));
        }

        // Handle strings that might come with or without quotes
        if (targetType == String.class) {
            if (json.startsWith("\"") && json.endsWith("\"")) {
                return json.substring(1, json.length() - 1);
            }
            return json;
        }

        return objectMapper.readValue(json, targetType);
    }

    /**
     * Parses multiple inputs for method with multiple parameters
     * @param inputJson JSON input string for multiple parameters
     * @param paramTypes Parameter types
     * @return Array of parsed input objects
     */
    public Object[] parseMultipleInputs(String inputJson, Class<?>[] paramTypes) throws JsonProcessingException {
        Object[] inputs = objectMapper.readValue(inputJson, Object[].class);
        Object[] methodParams = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            if (i < inputs.length) {
                String paramJson = objectMapper.writeValueAsString(inputs[i]);
                methodParams[i] = parseInput(paramJson, paramTypes[i]);
            } else {
                methodParams[i] = null;
            }
        }

        return methodParams;
    }

    /**
     * Replaces special tokens in the input JSON
     * @param json Input JSON string
     * @return JSON with special tokens replaced
     */
    private String replaceSpecialTokens(String json) {
        if (json == null) {
            return null;
        }

        // Replace Integer constants
        json = json.replace("Integer.MAX_VALUE", String.valueOf(Integer.MAX_VALUE));
        json = json.replace("Integer.MIN_VALUE", String.valueOf(Integer.MIN_VALUE));

        // Replace other constants if needed
        json = json.replace("Long.MAX_VALUE", String.valueOf(Long.MAX_VALUE));
        json = json.replace("Long.MIN_VALUE", String.valueOf(Long.MIN_VALUE));

        return json;
    }

    /**
     * Infers the element type for collections based on JSON content
     * @param json JSON string representing a collection
     * @return Inferred element type
     */
    private Class<?> inferElementType(String json) {
        // Simplistic inference based on JSON content
        if (json.contains("\"name\"") && json.contains("\"price\"")) {
            // Looks like a Product
            return findClassByName("Product");
        } else if (json.contains("\"username\"") && json.contains("\"email\"")) {
            // Looks like a User
            return findClassByName("User");
        } else {
            // Default to Object
            return Object.class;
        }
    }

    /**
     * Finds a class by name in the models package
     * @param className Simple class name
     * @return Found class or Object.class if not found
     */
    private Class<?> findClassByName(String className) {
        try {
            // Try with fully qualified name
            return Class.forName("com.reflectiontest.springReflectionTest.models." + className);
        } catch (ClassNotFoundException e) {
            // Fall back to Object if not found
            return Object.class;
        }
    }
}