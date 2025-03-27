package com.reflectiontest.springReflectionTest.util;

/**
 * H2 in-memory database implementation of DatabaseManager
 */
class H2Manager implements DatabaseManager {
    private static final String CONNECTION_STRING = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";
    private boolean started = false;

    @Override
    public void start() {
        // H2 in-memory database doesn't need to be started
        started = true;
        System.out.println("✅ H2 in-memory database available at: " + getConnectionString());
    }

    @Override
    public void stop() {
        // H2 in-memory database doesn't need to be stopped
        started = false;
        System.out.println("🛑 H2 in-memory database stopped.");
    }

    @Override
    public String getConnectionString() {
        return started ? CONNECTION_STRING : null;
    }
}
