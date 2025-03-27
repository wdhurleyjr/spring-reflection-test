package com.reflectiontest.springReflectionTest.util;

/**
 * MongoDB implementation of DatabaseManager
 */
class MongoDBManager implements DatabaseManager {
    private static final String IMAGE_NAME = "mongo:6.0";
    private TestContainer mongoContainer;

    @Override
    public void start() {
        mongoContainer = new TestContainer(IMAGE_NAME);
        mongoContainer.start();
        System.out.println("✅ MongoDB container started at: " + getConnectionString());
    }

    @Override
    public void stop() {
        if (mongoContainer != null) {
            mongoContainer.stop();
            System.out.println("🛑 MongoDB container stopped.");
        }
    }

    @Override
    public String getConnectionString() {
        return mongoContainer != null ? mongoContainer.getConnectionString() : null;
    }
}
