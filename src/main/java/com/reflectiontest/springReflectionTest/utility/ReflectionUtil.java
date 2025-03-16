package com.reflectiontest.springReflectionTest.utility;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class ReflectionUtil {

    public static Map<String, String> getModelStructure(Class<?> modelClass) {
        Map<String, String> fieldMap = new HashMap<>();
        for (Field field : modelClass.getDeclaredFields()) {
            fieldMap.put(field.getName(), field.getType().getSimpleName());
        }
        return fieldMap;
    }
}
