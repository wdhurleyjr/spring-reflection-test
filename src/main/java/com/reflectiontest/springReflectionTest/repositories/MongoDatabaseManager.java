package com.reflectiontest.springReflectionTest.repositories;

import com.reflectiontest.springReflectionTest.utility.DatabaseManager;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

public class MongoDatabaseManager {
    private static final String IMAGE_NAME = "mongo:6.0";
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse(IMAGE_NAME));

    public static void start() {
        mongoDBContainer.start();
        System.out.println("✅ MongoDB TestContainer started at: " + getConnectionString());
    }

    public static void stop() {
        mongoDBContainer.stop();
        System.out.println("🛑 MongoDB TestContainer stopped.");
    }

    public static String getConnectionString() {
        return mongoDBContainer.getReplicaSetUrl();
    }
}




