package com.reflectiontest.springReflectionTest.repositories;

import com.reflectiontest.springReflectionTest.annotations.MockReturn;
import com.reflectiontest.springReflectionTest.annotations.MockReturns;

/**
 * Authentication repository for tracking login attempts and account status
 */
public interface AuthenticationRepository {
    /**
     * Records a login attempt
     */
    @MockReturns({
            @MockReturn(inputJson = "[\"johndoe\", true, \"192.168.1.1\"]", returnJson = ""),
            @MockReturn(inputJson = "[\"admin\", false, \"10.0.0.1\"]", returnJson = "")
    })
    void recordLoginAttempt(String username, boolean successful, String ipAddress);

    /**
     * Checks if an account is locked
     */
    @MockReturns({
            @MockReturn(inputJson = "\"johndoe\"", returnJson = "false", isDefault = true),
            @MockReturn(inputJson = "\"lockedUser\"", returnJson = "true"),
            @MockReturn(inputJson = "\"admin\"", returnJson = "false")
    })
    boolean isAccountLocked(String username);

    /**
     * Gets the count of failed login attempts for a user
     */
    @MockReturns({
            @MockReturn(inputJson = "\"johndoe\"", returnJson = "0", isDefault = true),
            @MockReturn(inputJson = "\"lockedUser\"", returnJson = "5"),
            @MockReturn(inputJson = "\"repeatOffender\"", returnJson = "3")
    })
    int getFailedLoginAttempts(String username);
}




