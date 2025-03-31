package com.reflectiontest.springReflectionTest.testrunner.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Utility class for comparing JSON contents
 */
public class JsonComparator {
    private static final Logger logger = LoggerFactory.getLogger(JsonComparator.class);

    private final ObjectMapper objectMapper;

    public JsonComparator() {
        this.objectMapper = createConfiguredObjectMapper();
    }

    /**
     * Creates a configured ObjectMapper with necessary modules and settings
     */
    private ObjectMapper createConfiguredObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return mapper;
    }

    /**
     * Compares JSON objects for equality, ignoring property order
     * @param expected Expected JSON string
     * @param actual Actual JSON string
     * @return Whether contents are equivalent
     */
    public boolean compareJsonContents(String expected, String actual) {
        try {
            // Parse both to Maps or Lists
            if (expected.startsWith("{") && actual.startsWith("{")) {
                Map<String, Object> expectedMap = objectMapper.readValue(expected, Map.class);
                Map<String, Object> actualMap = objectMapper.readValue(actual, Map.class);
                return compareMaps(expectedMap, actualMap);
            } else if (expected.startsWith("[") && actual.startsWith("[")) {
                List<Object> expectedList = objectMapper.readValue(expected, List.class);
                List<Object> actualList = objectMapper.readValue(actual, List.class);
                return compareLists(expectedList, actualList);
            }
        } catch (JsonProcessingException e) {
            logger.debug("Failed to compare as JSON objects: {}", e.getMessage());
        }

        // Fall back to string comparison
        return expected.equals(actual);
    }

    /**
     * Recursively compare maps, handling nested objects
     */
    private boolean compareMaps(Map<String, Object> expected, Map<String, Object> actual) {
        if (expected.size() != actual.size()) return false;

        for (String key : expected.keySet()) {
            if (!actual.containsKey(key)) return false;

            Object expectedValue = expected.get(key);
            Object actualValue = actual.get(key);

            if (expectedValue == null && actualValue == null) {
                continue;
            }

            if (expectedValue == null || actualValue == null) {
                return false;
            }

            if (expectedValue instanceof Map && actualValue instanceof Map) {
                if (!compareMaps((Map<String, Object>)expectedValue, (Map<String, Object>)actualValue)) {
                    return false;
                }
            } else if (expectedValue instanceof List && actualValue instanceof List) {
                if (!compareLists((List<Object>)expectedValue, (List<Object>)actualValue)) {
                    return false;
                }
            } else if (!expectedValue.equals(actualValue)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Compare lists (order-sensitive)
     */
    private boolean compareLists(List<Object> expected, List<Object> actual) {
        if (expected.size() != actual.size()) return false;

        for (int i = 0; i < expected.size(); i++) {
            Object expectedItem = expected.get(i);
            Object actualItem = actual.get(i);

            if (expectedItem instanceof Map && actualItem instanceof Map) {
                if (!compareMaps((Map<String, Object>)expectedItem, (Map<String, Object>)actualItem)) {
                    return false;
                }
            } else if (expectedItem instanceof List && actualItem instanceof List) {
                if (!compareLists((List<Object>)expectedItem, (List<Object>)actualItem)) {
                    return false;
                }
            } else if (!expectedItem.equals(actualItem)) {
                return false;
            }
        }

        return true;
    }
}
