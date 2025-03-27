package com.reflectiontest.springReflectionTest.repositories;

/**
 * Repository for password reset tokens
 */
public interface TokenRepository {
    /**
     * Generates a reset token for a user
     */
    String generateResetToken(String username);

    /**
     * Validates a reset token
     */
    boolean validateResetToken(String username, String token);

    /**
     * Invalidates a reset token
     */
    void invalidateResetToken(String username);
}
