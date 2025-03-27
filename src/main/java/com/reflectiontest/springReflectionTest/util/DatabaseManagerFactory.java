package com.reflectiontest.springReflectionTest.util;

/**
 * Factory for creating database managers
 */
public class DatabaseManagerFactory {
    /**
     * Creates a database manager for the specified database type
     */
    public static DatabaseManager createDatabaseManager(String databaseType) {
        switch (databaseType.toLowerCase()) {
            case "mongodb":
                return new MongoDBManager();
            case "postgresql":
                return new PostgreSQLManager();
            case "mysql":
                return new MySQLManager();
            case "h2":
                return new H2Manager();
            default:
                throw new IllegalArgumentException("Unsupported database type: " + databaseType);
        }
    }
}
