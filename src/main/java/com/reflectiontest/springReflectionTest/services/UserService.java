package com.reflectiontest.springReflectionTest.services;

import com.reflectiontest.springReflectionTest.annotations.ExpectedResult;
import com.reflectiontest.springReflectionTest.annotations.IntegrationTest;
import com.reflectiontest.springReflectionTest.annotations.MockDependency;
import com.reflectiontest.springReflectionTest.models.User;
import com.reflectiontest.springReflectionTest.repositories.AuthenticationRepository;
import com.reflectiontest.springReflectionTest.repositories.TokenRepository;
import com.reflectiontest.springReflectionTest.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * User service that demonstrates various testing scenarios
 * including complex objects, collections, exceptions, and security patterns.
 *
 * Implementation is stateless - relies on injected repositories for all data access.
 */
@Service
public class UserService {

    @MockDependency
    private UserRepository userRepository;

    @MockDependency
    private AuthenticationRepository authRepository;

    @MockDependency
    private TokenRepository tokenRepository;

    public UserService() {
        // Default no-arg constructor
    }

    public UserService(UserRepository userRepository, AuthenticationRepository authRepository, TokenRepository tokenRepository) {
        this.userRepository = userRepository;
        this.authRepository = authRepository;
        this.tokenRepository = tokenRepository;
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "{\"username\": \"johndoe\", \"password\": \"password123\"}", expectedJson = "true")
    @ExpectedResult(inputJson = "{\"username\": \"\", \"password\": \"password123\"}", expectedJson = "false")
    @ExpectedResult(inputJson = "{\"username\": \"johndoe\", \"password\": \"\"}", expectedJson = "false")
    @ExpectedResult(inputJson = "{\"username\": \"admin\", \"password\": \"admin\"}", expectedJson = "false")
    @ExpectedResult(inputJson = "{\"username\": null, \"password\": \"password123\"}", expectedJson = "false")
    public boolean registerUser(User user) {
        if (user == null || user.getUsername() == null || user.getUsername().isEmpty() ||
                user.getPassword() == null || user.getPassword().isEmpty()) {
            return false;
        }

        // Disallow common usernames for security
        if (user.getUsername().equalsIgnoreCase("admin")) {
            return false;
        }

        if (userRepository.existsByUsername(user.getUsername())) {
            return false;
        }

        // Save user to repository
        userRepository.save(user);
        return true;
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "[{\"username\": \"johndoe\", \"password\": \"password123\"}, \"192.168.1.1\"]", expectedJson = "{\"success\": true, \"message\": \"Login successful\"}")
    @ExpectedResult(inputJson = "[{\"username\": \"johndoe\", \"password\": \"wrongpassword\"}, \"192.168.1.1\"]", expectedJson = "{\"success\": false, \"message\": \"Invalid username or password\"}")
    @ExpectedResult(inputJson = "[{\"username\": \"doesnotexist\", \"password\": \"password123\"}, \"192.168.1.1\"]", expectedJson = "{\"success\": false, \"message\": \"Invalid username or password\"}")
    @ExpectedResult(inputJson = "[{\"username\": \"lockedUser\", \"password\": \"password123\"}, \"192.168.1.1\"]", expectedJson = "{\"success\": false, \"message\": \"Account is locked due to too many failed attempts\"}")
    public Map<String, Object> authenticateUser(User user, String ipAddress) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();

        // Check for null values
        if (user == null || user.getUsername() == null) {
            result.put("success", false);
            result.put("message", "Invalid username or password");
            return result;
        }

        // Check if user is locked out
        if (authRepository.isAccountLocked(user.getUsername())) {
            result.put("success", false);
            result.put("message", "Account is locked due to too many failed attempts");
            return result;
        }

        // Get user from repository
        Optional<User> storedUser = userRepository.findByUsername(user.getUsername());

        // Verify credentials
        boolean authenticated = storedUser.isPresent() &&
                user.getPassword() != null &&
                user.getPassword().equals(storedUser.get().getPassword());

        // Track login attempt - this updates the lock status if needed
        authRepository.recordLoginAttempt(user.getUsername(), authenticated, ipAddress);

        result.put("success", authenticated);
        result.put("message", authenticated ? "Login successful" : "Invalid username or password");

        return result;
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "[\"johndoe\", \"password123\", \"newpassword456\"]", expectedJson = "true")
    @ExpectedResult(inputJson = "[\"johndoe\", \"wrongpassword\", \"newpassword456\"]", expectedJson = "false")
    @ExpectedResult(inputJson = "[\"doesnotexist\", \"password123\", \"newpassword456\"]", expectedJson = "false")
    @ExpectedResult(inputJson = "[\"johndoe\", \"password123\", \"weak\"]", expectedJson = "false")
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            return false;
        }

        User user = userOptional.get();
        if (!user.getPassword().equals(oldPassword)) {
            return false;
        }

        // Password strength validation
        if (newPassword.length() < 8) {
            return false;
        }

        // Update password
        user.setPassword(newPassword);
        userRepository.save(user);

        return true;
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "\"johndoe\"", expectedJson = "true")
    @ExpectedResult(inputJson = "\"doesnotexist\"", expectedJson = "false")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "false")
    public boolean isUserRegistered(String username) {
        return username != null && userRepository.existsByUsername(username);
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
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();

        int existingCount = 0;
        int newCount = 0;

        for (String username : usernames) {
            if (userRepository.existsByUsername(username)) {
                existingCount++;
            } else {
                newCount++;
            }
        }

        // Return in expected order for consistent test results
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

        Optional<User> user = userRepository.findByUsername(username);
        if (user.isEmpty()) {
            throw new NoSuchElementException("User not found: " + username);
        }

        return user.get().getEmail();
    }

    /**
     * Test with optional return types
     */
    @ExpectedResult(inputJson = "\"johndoe\"", expectedJson = "{\"present\":true,\"value\":{\"username\":\"johndoe\",\"password\":\"masked\"}}")
    @ExpectedResult(inputJson = "\"unknown\"", expectedJson = "{\"present\":false}")
    @ExpectedResult(inputJson = "__NULL__", expectedJson = "{\"present\":false}")
    public Optional<Map<String, String>> getUserInfo(String username) {
        if (username == null) {
            return Optional.empty();
        }

        Optional<User> user = userRepository.findByUsername(username);
        if (user.isEmpty()) {
            return Optional.empty();
        }

        LinkedHashMap<String, String> userInfo = new LinkedHashMap<>();
        userInfo.put("username", user.get().getUsername());
        userInfo.put("password", "masked"); // Never expose real passwords

        return Optional.of(userInfo);
    }

    /**
     * Test with more complex business logic
     */
    @ExpectedResult(inputJson = "\"johndoe\"", expectedJson = "\"token123\"")
    @ExpectedResult(inputJson = "\"unknown\"", expectedJson = "__THROWS__")
    public String generatePasswordResetToken(String username) {
        if (!userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("User not found");
        }

        // Generate a reset token
        String token = tokenRepository.generateResetToken(username);
        return token;
    }

    /**
     * Test with multiple parameters of different types
     */
    @ExpectedResult(inputJson = "[\"johndoe\", \"token123\", \"newSecurePass1!\"]", expectedJson = "true")
    @ExpectedResult(inputJson = "[\"johndoe\", \"wrongtoken\", \"newSecurePass1!\"]", expectedJson = "false")
    @ExpectedResult(inputJson = "[\"johndoe\", \"token123\", \"weak\"]", expectedJson = "false")
    public boolean resetPassword(String username, String token, String newPassword) {
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            return false;
        }

        // Verify the token
        if (!tokenRepository.validateResetToken(username, token)) {
            return false;
        }

        // Verify password strength
        if (!isPasswordStrong(newPassword)) {
            return false;
        }

        // Update password
        User user = userOptional.get();
        user.setPassword(newPassword);
        userRepository.save(user);

        // Invalidate the token after use
        tokenRepository.invalidateResetToken(username);

        return true;
    }
}