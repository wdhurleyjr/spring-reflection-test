package com.reflectiontest.springReflectionTest.util;

/**
 * MySQL implementation of DatabaseManager
 */
class MySQLManager implements DatabaseManager {
    private static final String IMAGE_NAME = "mysql:8.0";
    private TestContainer mysqlContainer;

    @Override
    public void start() {
        mysqlContainer = new TestContainer(IMAGE_NAME);
        mysqlContainer.addEnv("MYSQL_ROOT_PASSWORD", "password");
        mysqlContainer.addEnv("MYSQL_DATABASE", "testdb");
        mysqlContainer.start();
        System.out.println("✅ MySQL container started at: " + getConnectionString());
    }

    @Override
    public void stop() {
        if (mysqlContainer != null) {
            mysqlContainer.stop();
            System.out.println("🛑 MySQL container stopped.");
        }
    }

    @Override
    public String getConnectionString() {
        return mysqlContainer != null ?
                "jdbc:mysql://" + mysqlContainer.getHost() + ":" + mysqlContainer.getPort() + "/testdb" : null;
    }
}
