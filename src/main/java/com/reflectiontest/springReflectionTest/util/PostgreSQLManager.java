package com.reflectiontest.springReflectionTest.util;

/**
 * PostgreSQL implementation of DatabaseManager
 */
class PostgreSQLManager implements DatabaseManager {
    private static final String IMAGE_NAME = "postgres:14";
    private TestContainer postgresContainer;

    @Override
    public void start() {
        postgresContainer = new TestContainer(IMAGE_NAME);
        postgresContainer.addEnv("POSTGRES_PASSWORD", "postgres");
        postgresContainer.addEnv("POSTGRES_USER", "postgres");
        postgresContainer.addEnv("POSTGRES_DB", "testdb");
        postgresContainer.start();
        System.out.println("✅ PostgreSQL container started at: " + getConnectionString());
    }

    @Override
    public void stop() {
        if (postgresContainer != null) {
            postgresContainer.stop();
            System.out.println("🛑 PostgreSQL container stopped.");
        }
    }

    @Override
    public String getConnectionString() {
        return postgresContainer != null ?
                "jdbc:postgresql://" + postgresContainer.getHost() + ":" + postgresContainer.getPort() + "/testdb" : null;
    }
}
