package com.reflectiontest.springReflectionTest.repositories;

import com.reflectiontest.springReflectionTest.annotations.MockReturn;
import com.reflectiontest.springReflectionTest.annotations.MockReturns;

/**
 * Repository for password reset tokens with mock return annotations
 */
public interface TokenRepository {
    /**
     * Generates a reset token for a user
     */
    @MockReturns({
            @MockReturn(inputJson = "\"johndoe\"", returnJson = "\"token123\""),
            @MockReturn(inputJson = "\"admin\"", returnJson = "\"token456\""),
            @MockReturn(inputJson = "\"newuser\"", returnJson = "\"token789\"")
    })
    String generateResetToken(String username);

    /**
     * Validates a reset token
     */
    @MockReturns({
            @MockReturn(inputJson = "[\"johndoe\", \"token123\"]", returnJson = "true"),
            @MockReturn(inputJson = "[\"admin\", \"token456\"]", returnJson = "true"),
            @MockReturn(inputJson = "[\"johndoe\", \"wrongtoken\"]", returnJson = "false", isDefault = true)
    })
    boolean validateResetToken(String username, String token);

    /**
     * Invalidates a reset token
     */
    @MockReturns({
            @MockReturn(inputJson = "\"johndoe\"", returnJson = ""),
            @MockReturn(inputJson = "\"admin\"", returnJson = "")
    })
    void invalidateResetToken(String username);
}