package com.reflectiontest.springReflectionTest.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility for generating and managing bytecode hashes for methods.
 * Used to detect when a method implementation has changed.
 */
public class BytecodeHashUtil {
    private static final Logger logger = LoggerFactory.getLogger(BytecodeHashUtil.class);
    private static final String HASH_STORAGE_DIRECTORY = ".reflecttest/hashes";
    private static final Map<String, String> hashCache = new ConcurrentHashMap<>();

    /**
     * Generates a hash of the method's bytecode
     *
     * @param clazz The class containing the method
     * @param method The method to generate a hash for
     * @return A Base64 encoded hash string
     */
    public static String generateMethodHash(Class<?> clazz, Method method) {
        String methodKey = getMethodKey(clazz, method);

        // Check cache first
        if (hashCache.containsKey(methodKey)) {
            return hashCache.get(methodKey);
        }

        try {
            // For a real implementation, you would use ASM or Javassist to get actual bytecode
            // This simplified version just hashes method signature characteristics
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);

            // Write class name
            dos.writeUTF(clazz.getName());

            // Write method name
            dos.writeUTF(method.getName());

            // Write return type
            dos.writeUTF(method.getReturnType().getName());

            // Write parameter types
            dos.writeInt(method.getParameterTypes().length);
            for (Class<?> paramType : method.getParameterTypes()) {
                dos.writeUTF(paramType.getName());
            }

            // Write exception types
            dos.writeInt(method.getExceptionTypes().length);
            for (Class<?> exceptionType : method.getExceptionTypes()) {
                dos.writeUTF(exceptionType.getName());
            }

            // Generate SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bos.toByteArray());

            // Convert to Base64 for easy storage and comparison
            String hashString = Base64.getEncoder().encodeToString(hash);

            // Cache the result
            hashCache.put(methodKey, hashString);

            return hashString;

        } catch (NoSuchAlgorithmException | IOException e) {
            logger.error("Error generating method hash: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets the stored hash for a method, if available
     *
     * @param clazz The class containing the method
     * @param method The method to get the hash for
     * @return The stored hash, or null if not found
     */
    public static String getStoredMethodHash(Class<?> clazz, Method method) {
        String methodKey = getMethodKey(clazz, method);
        String hashFilePath = getHashFilePath(methodKey);

        // Check if the hash file exists
        File hashFile = new File(hashFilePath);
        if (!hashFile.exists()) {
            return null;
        }

        try {
            // Read the hash from the file
            String hash = Files.readString(Paths.get(hashFilePath)).trim();

            // Cache the result
            hashCache.put(methodKey, hash);

            return hash;
        } catch (IOException e) {
            logger.error("Error reading method hash: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Stores a hash for a method
     *
     * @param clazz The class containing the method
     * @param method The method to store the hash for
     * @param hash The hash to store
     * @return true if successful, false otherwise
     */
    public static boolean storeMethodHash(Class<?> clazz, Method method, String hash) {
        String methodKey = getMethodKey(clazz, method);
        String hashFilePath = getHashFilePath(methodKey);

        try {
            // Create directories if they don't exist
            File hashFile = new File(hashFilePath);
            hashFile.getParentFile().mkdirs();

            // Write the hash to the file
            Files.writeString(Paths.get(hashFilePath), hash);

            // Update the cache
            hashCache.put(methodKey, hash);

            return true;
        } catch (IOException e) {
            logger.error("Error storing method hash: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generates a unique key for a method
     */
    private static String getMethodKey(Class<?> clazz, Method method) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(clazz.getName());
        keyBuilder.append("#");
        keyBuilder.append(method.getName());
        keyBuilder.append("(");

        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            keyBuilder.append(paramTypes[i].getName());
            if (i < paramTypes.length - 1) {
                keyBuilder.append(",");
            }
        }

        keyBuilder.append(")");

        return keyBuilder.toString();
    }

    /**
     * Gets the file path for storing a method hash
     */
    private static String getHashFilePath(String methodKey) {
        // Replace problematic characters in the method key
        String safeKey = methodKey.replace('.', '/').replace('#', '-').replace('(', '_').replace(')', '_').replace(',', '-');

        return HASH_STORAGE_DIRECTORY + "/" + safeKey + ".hash";
    }

    /**
     * Clears the hash cache
     */
    public static void clearCache() {
        hashCache.clear();
    }

    /**
     * Determines if a method's bytecode has changed since the last stored hash
     *
     * @param clazz The class containing the method
     * @param method The method to check
     * @return true if changed, false if not changed or no previous hash
     */
    public static boolean hasMethodChanged(Class<?> clazz, Method method) {
        String storedHash = getStoredMethodHash(clazz, method);

        // If no stored hash, we consider it as changed
        if (storedHash == null) {
            return true;
        }

        String currentHash = generateMethodHash(clazz, method);

        // Compare the hashes
        return !storedHash.equals(currentHash);
    }

    /**
     * Updates the stored hash for a method if it has changed
     *
     * @param clazz The class containing the method
     * @param method The method to update
     * @return true if updated, false if no update was needed or failed
     */
    public static boolean updateMethodHashIfChanged(Class<?> clazz, Method method) {
        if (hasMethodChanged(clazz, method)) {
            String newHash = generateMethodHash(clazz, method);
            return storeMethodHash(clazz, method, newHash);
        }

        return false;
    }
}
