package com.reflectiontest.springReflectionTest.testrunner.core;

import com.reflectiontest.springReflectionTest.util.BytecodeHashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing and verifying method bytecode integrity
 */
public class BytecodeVerificationService {
    private static final Logger logger = LoggerFactory.getLogger(BytecodeVerificationService.class);

    // Cache to store verification results
    private final Map<String, VerificationResult> verificationCache = new HashMap<>();

    /**
     * Represents the result of a bytecode verification
     */
    public static class VerificationResult {
        private final boolean hasChanged;
        private final String currentHash;
        private final String previousHash;

        public VerificationResult(boolean hasChanged, String currentHash, String previousHash) {
            this.hasChanged = hasChanged;
            this.currentHash = currentHash;
            this.previousHash = previousHash;
        }

        public boolean hasChanged() {
            return hasChanged;
        }

        public String getCurrentHash() {
            return currentHash;
        }

        public String getPreviousHash() {
            return previousHash;
        }
    }

    /**
     * Generates a unique key for a method
     * @param clazz Class containing the method
     * @param method Method to generate key for
     * @return Unique method identifier
     */
    private String getMethodKey(Class<?> clazz, Method method) {
        return clazz.getName() + "#" + method.getName() +
                method.getParameterTypes().length;
    }

    /**
     * Verifies if a method's bytecode has changed
     * @param clazz Class containing the method
     * @param method Method to verify
     * @return VerificationResult containing change status
     */
    public VerificationResult verifyMethodBytecode(Class<?> clazz, Method method) {
        String methodKey = getMethodKey(clazz, method);

        try {
            // Generate current hash
            String currentHash = BytecodeHashUtil.generateMethodHash(clazz, method);

            // Retrieve stored hash
            String storedHash = BytecodeHashUtil.getStoredMethodHash(clazz, method);

            // Determine if method has changed
            boolean hasChanged = storedHash == null || !currentHash.equals(storedHash);

            // Create verification result
            VerificationResult result = new VerificationResult(hasChanged, currentHash, storedHash);

            // Cache the result
            verificationCache.put(methodKey, result);

            // Log changes
            if (hasChanged) {
                logger.warn("Method bytecode changed: {}.{}",
                        clazz.getSimpleName(), method.getName());
            }

            return result;
        } catch (Exception e) {
            logger.error("Error verifying bytecode for {}.{}: {}",
                    clazz.getSimpleName(), method.getName(), e.getMessage());

            // Return a default result in case of error
            return new VerificationResult(true, null, null);
        }
    }

    /**
     * Updates the stored hash for a method
     * @param clazz Class containing the method
     * @param method Method to update
     * @return true if hash was updated, false otherwise
     */
    public boolean updateMethodHash(Class<?> clazz, Method method) {
        return BytecodeHashUtil.updateMethodHashIfChanged(clazz, method);
    }

    /**
     * Retrieves the cached verification result for a method
     * @param clazz Class containing the method
     * @param method Method to retrieve result for
     * @return Cached VerificationResult or null if not found
     */
    public VerificationResult getCachedVerificationResult(Class<?> clazz, Method method) {
        String methodKey = getMethodKey(clazz, method);
        return verificationCache.get(methodKey);
    }

    /**
     * Clears the verification cache
     */
    public void clearVerificationCache() {
        verificationCache.clear();
    }
}
