package com.reflectiontest.springReflectionTest.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Interface for managing database connections during integration tests
 */
public interface DatabaseManager {
    /**
     * Starts the database
     */
    void start();

    /**
     * Stops the database
     */
    void stop();

    /**
     * Gets the connection string for the database
     */
    String getConnectionString();
}


