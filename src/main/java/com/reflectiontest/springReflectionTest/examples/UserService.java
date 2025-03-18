package com.reflectiontest.springReflectionTest.examples;

import com.reflectiontest.springReflectionTest.annotations.ExpectedResult;
import com.reflectiontest.springReflectionTest.annotations.MockDependency;
import com.reflectiontest.springReflectionTest.repositories.UserRepository;
import org.springframework.stereotype.Service;
import com.reflectiontest.springReflectionTest.models.User;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {

    @MockDependency
    private UserRepository userRepository;

    private final Map<String, User> userCache = new HashMap<>();

    // Register a new user
    @ExpectedResult(inputJson = "{\"username\": \"johndoe\", \"password\": \"password123\"}", expectedJson = "true")
    @ExpectedResult(inputJson = "{\"username\": \"\", \"password\": \"password123\"}", expectedJson = "false")
    @ExpectedResult(inputJson = "{\"username\": \"johndoe\", \"password\": \"\"}", expectedJson = "false")
    public boolean registerUser(User user) {
        if (user == null || user.getUsername().isEmpty() || user.getPassword().isEmpty()) {
            return false;
        }
        if (userRepository.existsByUsername(user.getUsername())) {
            return false; // User already exists
        }
        userCache.put(user.getUsername(), user);
        return true;
    }

    // Authenticate user credentials
    @ExpectedResult(inputJson = "{\"username\": \"johndoe\", \"password\": \"password123\"}", expectedJson = "true")
    @ExpectedResult(inputJson = "{\"username\": \"johndoe\", \"password\": \"wrongpassword\"}", expectedJson = "false")
    @ExpectedResult(inputJson = "{\"username\": \"doesnotexist\", \"password\": \"password123\"}", expectedJson = "false")
    public boolean authenticateUser(User user) {
        if (user == null || !userCache.containsKey(user.getUsername())) {
            return false;
        }
        return userCache.get(user.getUsername()).getPassword().equals(user.getPassword());
    }

    // Change user password
    @ExpectedResult(inputJson = "[\"johndoe\", \"password123\", \"newpassword456\"]", expectedJson = "true")
    @ExpectedResult(inputJson = "[\"johndoe\", \"wrongpassword\", \"newpassword456\"]", expectedJson = "false")
    @ExpectedResult(inputJson = "[\"doesnotexist\", \"password123\", \"newpassword456\"]", expectedJson = "false")
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        if (!userCache.containsKey(username)) {
            return false;
        }
        User user = userCache.get(username);
        if (!user.getPassword().equals(oldPassword)) {
            return false;
        }
        user.setPassword(newPassword);
        return true;
    }

    // Check if a user exists
    @ExpectedResult(inputJson = "\"johndoe\"", expectedJson = "true")
    @ExpectedResult(inputJson = "\"doesnotexist\"", expectedJson = "false")
    public boolean isUserRegistered(String username) {
        return userCache.containsKey(username);
    }

    // Validate password strength (at least 8 characters, one digit, one special character)
    @ExpectedResult(inputJson = "\"StrongPass1!\"", expectedJson = "true")
    @ExpectedResult(inputJson = "\"weakpass\"", expectedJson = "false")
    @ExpectedResult(inputJson = "\"12345678\"", expectedJson = "false")
    @ExpectedResult(inputJson = "\"NoSpecial123\"", expectedJson = "false")
    public boolean isPasswordStrong(String password) {
        return password.matches("^(?=.*[0-9])(?=.*[!@#$%^&*]).{8,}$");
    }
}





