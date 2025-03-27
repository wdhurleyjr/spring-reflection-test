package com.reflectiontest.springReflectionTest.examples;

import com.reflectiontest.springReflectionTest.annotations.ExpectedResult;
import com.reflectiontest.springReflectionTest.annotations.IntegrationTest;
import com.reflectiontest.springReflectionTest.annotations.MockDependency;
import com.reflectiontest.springReflectionTest.models.User;
import com.reflectiontest.springReflectionTest.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * User service that demonstrates various testing scenarios
 * including complex objects, collections, exceptions, and security patterns.
 */
@Service
public class UserService {

    @MockDependency
    private UserRepository userRepository;

    private final Map<String, User> userCache = new HashMap<>();
    private final Map<String, List<LoginAttempt>> loginAttempts = new HashMap<>();
    private final Map<String, String> passwordResetTokens = new HashMap<>();

    /**
     * Helper class for tracking login attempts
     */
    private static class LoginAttempt {
        private final LocalDateTime timestamp;
        private final boolean successful;
        private final String ipAddress;

        public LoginAttempt(boolean successful, String ipAddress) {
            this.timestamp = LocalDateTime.now();
            this.successful = successful;
            this.ipAddress = ipAddress;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getIpAddress() {
            return ipAddress;
        }
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "{\"username\": \"johndoe\", \"password\": \"password123\"}", expectedJson = "true")
    @ExpectedResult(inputJson = "{\"username\": \"\", \"password\": \"password123\"}", expectedJson = "false")
    @ExpectedResult(inputJson = "{\"username\": \"johndoe\", \"password\": \"\"}", expectedJson = "false")
    @ExpectedResult(inputJson = "{\"username\": \"admin\", \"password\": \"admin\"}", expectedJson = "false")
    @ExpectedResult(inputJson = "{\"username\": null, \"password\": \"password123\"}", expectedJson = "false")
    public boolean registerUser(User user) {
        if (user == null || user.getUsername() == null || user.getUsername().isEmpty() || user.getPassword() == null || user.getPassword().isEmpty()) {
            return false;
        }

        // Disallow common usernames for security
        if (user.getUsername().equalsIgnoreCase("admin")) {
            return false;
        }

        if (userRepository.existsByUsername(user.getUsername()) || userCache.containsKey(user.getUsername())) {
            return false;
        }

        userCache.put(user.getUsername(), user);
        return true;
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "[{\"username\": \"johndoe\", \"password\": \"password123\"}, \"192.168.1.1\"]", expectedJson = "{\"success\": true, \"message\": \"Login successful\"}")
    @ExpectedResult(inputJson = "[{\"username\": \"johndoe\", \"password\": \"wrongpassword\"}, \"192.168.1.1\"]", expectedJson = "{\"success\": false, \"message\": \"Invalid username or password\"}")
    @ExpectedResult(inputJson = "[{\"username\": \"doesnotexist\", \"password\": \"password123\"}, \"192.168.1.1\"]", expectedJson = "{\"success\": false, \"message\": \"Invalid username or password\"}")
    @ExpectedResult(inputJson = "[{\"username\": \"lockedUser\", \"password\": \"password123\"}, \"192.168.1.1\"]", expectedJson = "{\"success\": false, \"message\": \"Account is locked due to too many failed attempts\"}")
    public Map<String, Object> authenticateUser(User user, String ipAddress) {
        Map<String, Object> result = new HashMap<>();

        // Check if user is locked out
        if (user != null && isUserLocked(user.getUsername())) {
            result.put("success", false);
            result.put("message", "Account is locked due to too many failed attempts");
            return result;
        }

        // Basic authentication
        boolean authenticated = user != null &&
                userCache.containsKey(user.getUsername()) &&
                userCache.get(user.getUsername()).getPassword().equals(user.getPassword());

        // Track login attempt
        recordLoginAttempt(user != null ? user.getUsername() : "unknown", authenticated, ipAddress);

        result.put("success", authenticated);
        result.put("message", authenticated ? "Login successful" : "Invalid username or password");

        return result;
    }

    /**
     * Check if a user is locked out due to too many failed attempts
     */
    private boolean isUserLocked(String username) {
        if (!loginAttempts.containsKey(username)) {
            return false;
        }

        List<LoginAttempt> attempts = loginAttempts.get(username);

        // Look at the last 5 attempts
        if (attempts.size() < 5) {
            return false;
        }

        // If the last 5 attempts were failures, and the most recent was within the last hour
        long failedCount = attempts.stream()
                .limit(5)
                .filter(attempt -> !attempt.isSuccessful())
                .count();

        if (failedCount >= 5) {
            LoginAttempt mostRecent = attempts.get(0);
            LocalDateTime lockoutThreshold = LocalDateTime.now().minusHours(1);
            return mostRecent.getTimestamp().isAfter(lockoutThreshold);
        }

        return false;
    }

    /**
     * Record a login attempt
     */
    private void recordLoginAttempt(String username, boolean successful, String ipAddress) {
        LoginAttempt attempt = new LoginAttempt(successful, ipAddress);
        if (!loginAttempts.containsKey(username)) {
            loginAttempts.put(username, new ArrayList<>());
        }

        // Add to the beginning of the list (most recent first)
        loginAttempts.get(username).add(0, attempt);
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "[\"johndoe\", \"password123\", \"newpassword456\"]", expectedJson = "true")
    @ExpectedResult(inputJson = "[\"johndoe\", \"wrongpassword\", \"newpassword456\"]", expectedJson = "false")
    @ExpectedResult(inputJson = "[\"doesnotexist\", \"password123\", \"newpassword456\"]", expectedJson = "false")
    @ExpectedResult(inputJson = "[\"johndoe\", \"password123\", \"weak\"]", expectedJson = "false")
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        if (!userCache.containsKey(username)) {
            return false;
        }

        User user = userCache.get(username);
        if (!user.getPassword().equals(oldPassword)) {
            return false;
        }

        // Password strength validation
        if (newPassword.length() < 8) {
            return false;
        }

        user.setPassword(newPassword);
        return true;
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "\"johndoe\"", expectedJson = "true")
    @ExpectedResult(inputJson = "\"doesnotexist\"", expectedJson = "false")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "false")
    public boolean isUserRegistered(String username) {
        return username != null && userCache.containsKey(username);
    }

    /**
     * Tests password strength with various patterns
     */
    @ExpectedResult(inputJson = "\"StrongPass1!\"", expectedJson = "true")
    @ExpectedResult(inputJson = "\"weakpass\"", expectedJson = "false")
    @ExpectedResult(inputJson = "\"12345678\"", expectedJson = "false")
    @ExpectedResult(inputJson = "\"NoSpecial123\"", expectedJson = "false")
    @ExpectedResult(inputJson = "\"!@#$%^&*()\"", expectedJson = "false")
    @ExpectedResult(inputJson = "\"aB1!aB1!aB1!aB1!aB1!\"", expectedJson = "true")
    @ExpectedResult(inputJson = "\"\"", expectedJson = "false")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "false")
    public boolean isPasswordStrong(String password) {
        if (password == null || password.isEmpty()) {
            return false;
        }

        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (!Character.isWhitespace(c)) {
                hasSpecial = true;
            }
        }

        return password.length() >= 8 && hasLetter && hasDigit && hasSpecial;
    }

    /**
     * Tests with complex return types and collections
     */
    @ExpectedResult(
            inputJson = "[\"johndoe\", \"janedoe\", \"bobsmith\"]",
            expectedJson = "{\"total\": 3, \"existing\": 1, \"new\": 2}"
    )
    @ExpectedResult(
            inputJson = "[]",
            expectedJson = "{\"total\": 0, \"existing\": 0, \"new\": 0}"
    )
    @ExpectedResult(
            inputJson = "[\"nonexistent1\", \"nonexistent2\"]",
            expectedJson = "{\"total\": 2, \"existing\": 0, \"new\": 2}"
    )
    public Map<String, Integer> analyzeUsernames(List<String> usernames) {
        Map<String, Integer> result = new HashMap<>();

        int existingCount = 0;
        int newCount = 0;

        for (String username : usernames) {
            if (userCache.containsKey(username)) {
                existingCount++;
            } else {
                newCount++;
            }
        }

        result.put("total", usernames.size());
        result.put("existing", existingCount);
        result.put("new", newCount);

        return result;
    }

    /**
     * Test with exceptions
     */
    @ExpectedResult(inputJson = "\"johndoe\"", expectedJson = "\"johndoe@example.com\"")
    @ExpectedResult(inputJson = "\"doesnotexist\"", expectedJson = "__THROWS__")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "__THROWS__")
    public String getUserEmail(String username) {
        if (username == null) {
            throw new IllegalArgumentException("Username cannot be null");
        }

        User user = userCache.get(username);
        if (user == null) {
            throw new NoSuchElementException("User not found: " + username);
        }

        // For test users, generate a fake email
        return username + "@example.com";
    }

    /**
     * Test with optional return types
     */
    @ExpectedResult(inputJson = "\"johndoe\"", expectedJson = "{\"present\":true,\"value\":{\"username\":\"johndoe\",\"password\":\"masked\"}}")
    @ExpectedResult(inputJson = "\"unknown\"", expectedJson = "{\"present\":false}")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "{\"present\":false}")
    public Optional<Map<String, String>> getUserInfo(String username) {
        if (username == null || !userCache.containsKey(username)) {
            return Optional.empty();
        }

        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("username", username);
        userInfo.put("password", "masked"); // Never return real passwords

        return Optional.of(userInfo);
    }

    /**
     * Test with more complex business logic
     */
    @ExpectedResult(inputJson = "\"johndoe\"", expectedJson = "\"token123\"")
    @ExpectedResult(inputJson = "\"unknown\"", expectedJson = "__THROWS__")
    public String generatePasswordResetToken(String username) {
        if (!userCache.containsKey(username)) {
            throw new IllegalArgumentException("User not found");
        }

        String token = "token" + Math.abs(username.hashCode() % 1000);
        passwordResetTokens.put(username, token);
        return token;
    }

    /**
     * Test with multiple parameters of different types
     */
    @ExpectedResult(inputJson = "[\"johndoe\", \"token123\", \"newSecurePass1!\"]", expectedJson = "true")
    @ExpectedResult(inputJson = "[\"johndoe\", \"wrongtoken\", \"newSecurePass1!\"]", expectedJson = "false")
    @ExpectedResult(inputJson = "[\"johndoe\", \"token123\", \"weak\"]", expectedJson = "false")
    public boolean resetPassword(String username, String token, String newPassword) {
        if (!userCache.containsKey(username)) {
            return false;
        }

        if (!passwordResetTokens.containsKey(username) || !passwordResetTokens.get(username).equals(token)) {
            return false;
        }

        if (!isPasswordStrong(newPassword)) {
            return false;
        }

        User user = userCache.get(username);
        user.setPassword(newPassword);
        passwordResetTokens.remove(username);

        return true;
    }
}