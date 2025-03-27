package com.reflectiontest.springReflectionTest.repositories;

import com.reflectiontest.springReflectionTest.models.User;
import java.util.Optional;

/**
 * Authentication repository for tracking login attempts and account status
 */
public interface AuthenticationRepository {
    /**
     * Records a login attempt
     */
    void recordLoginAttempt(String username, boolean successful, String ipAddress);

    /**
     * Checks if an account is locked
     */
    boolean isAccountLocked(String username);

    /**
     * Gets the count of failed login attempts for a user
     */
    int getFailedLoginAttempts(String username);
}




