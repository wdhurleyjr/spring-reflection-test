package com.reflectiontest.springReflectionTest.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Simple container abstraction for testing.
 * In a real implementation, this would use TestContainers or a similar library.
 */
class TestContainer {
    private final String imageName;
    private final Map<String, String> env = new HashMap<>();
    private boolean running = false;
    private String host = "localhost";
    private int port = 0;

    public TestContainer(String imageName) {
        this.imageName = imageName;
    }

    public void addEnv(String key, String value) {
        env.put(key, value);
    }

    public void start() {
        // In a real implementation, this would start a Docker container
        System.out.println("Starting container: " + imageName);

        // Simulate starting a container
        running = true;

        // Assign a random port to simulate container port binding
        port = 10000 + new Random().nextInt(1000);

        System.out.println("Container started on port: " + port);
    }

    public void stop() {
        // In a real implementation, this would stop the Docker container
        System.out.println("Stopping container: " + imageName);
        running = false;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getConnectionString() {
        if (!running) {
            return null;
        }

        // Construct a connection string based on the container type
        if (imageName.startsWith("mongo")) {
            return "mongodb://" + host + ":" + port;
        } else if (imageName.startsWith("postgres")) {
            return "jdbc:postgresql://" + host + ":" + port + "/testdb";
        } else if (imageName.startsWith("mysql")) {
            return "jdbc:mysql://" + host + ":" + port + "/testdb";
        }

        return host + ":" + port;
    }
}
