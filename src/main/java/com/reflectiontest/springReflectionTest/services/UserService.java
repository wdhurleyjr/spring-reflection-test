package com.reflectiontest.springReflectionTest.services;

import com.reflectiontest.springReflectionTest.annotations.ExpectedResult;
import com.reflectiontest.springReflectionTest.annotations.IntegrationTest;
import com.reflectiontest.springReflectionTest.models.User;
import com.reflectiontest.springReflectionTest.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "{\"username\": \"johndoe\", \"email\": \"john@example.com\", \"password\": \"password123\", \"role\": \"USER\"}", expectedJson = "true")
    @ExpectedResult(inputJson = "{\"username\": \"\", \"email\": \"user@example.com\", \"password\": \"password123\", \"role\": \"USER\"}", expectedJson = "false")
    @ExpectedResult(inputJson = "{\"username\": \"johndoe\", \"email\": \"user@example.com\", \"password\": \"\"}", expectedJson = "false")
    @ExpectedResult(inputJson = "{\"username\": \"admin\", \"email\": \"admin@example.com\", \"password\": \"admin\"}", expectedJson = "false")
    @ExpectedResult(inputJson = "{\"username\": null, \"email\": \"user@example.com\", \"password\": \"password123\", \"role\": \"USER\"}", expectedJson = "false")
    public boolean registerUser(User user) {
        // Validate input
        if (user == null ||
                user.getUsername() == null || user.getUsername().isEmpty() ||
                user.getPassword() == null || user.getPassword().isEmpty() ||
                user.getEmail() == null || !isValidEmail(user.getEmail())) {
            return false;
        }

        // Disallow certain usernames
        if (user.getUsername().equalsIgnoreCase("admin") ||
                user.getUsername().equalsIgnoreCase("root")) {
            return false;
        }

        // Check if username already exists
        if (userRepository.existsByUsername(user.getUsername())) {
            return false;
        }

        // Save user
        userRepository.save(user);
        return true;
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "\"USER\"", expectedJson = "1")
    @ExpectedResult(inputJson = "\"ADMIN\"", expectedJson = "0")
    @ExpectedResult(inputJson = "\"NONEXISTENT\"", expectedJson = "0")
    public long countUsersByRole(String role) {
        return userRepository.findByRole(role).size();
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "\"example.com\"", expectedJson = "2")
    @ExpectedResult(inputJson = "\"test.com\"", expectedJson = "0")
    public long countUsersByEmailDomain(String domain) {
        return userRepository.findByEmailContaining(domain).size();
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "[\"USER\", \"example.com\"]", expectedJson = "1")
    @ExpectedResult(inputJson = "[\"ADMIN\", \"admin.com\"]", expectedJson = "0")
    public long countUsersByRoleAndEmailDomain(String role, String domain) {
        return userRepository.findUsersByRoleAndEmailDomain(role, ".*" + domain).size();
    }

    private boolean isValidEmail(String email) {
        if (email == null) return false;

        // Basic email validation regex
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        return Pattern.compile(emailRegex)
                .matcher(email)
                .matches();
    }

    @IntegrationTest
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

        // Enhanced password complexity check
        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        boolean hasUppercase = false;
        boolean hasLowercase = false;

        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
                if (Character.isUpperCase(c)) hasUppercase = true;
                if (Character.isLowerCase(c)) hasLowercase = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (!Character.isWhitespace(c)) {
                hasSpecial = true;
            }
        }

        return password.length() >= 8 &&
                hasLetter &&
                hasDigit &&
                hasSpecial &&
                hasUppercase &&
                hasLowercase;
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "[\"johndoe\", \"janedoe\", \"bobsmith\"]",
            expectedJson = "{\"total\": 3, \"existing\": 1, \"new\": 2, \"existingUsernames\": [\"johndoe\"]}")
    @ExpectedResult(inputJson = "[]",
            expectedJson = "{\"total\": 0, \"existing\": 0, \"new\": 0, \"existingUsernames\": []}")
    public Map<String, Object> analyzeUsernames(List<String> usernames) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<String> existingUsernames = new ArrayList<>();
        int existingCount = 0;
        int newCount = 0;

        for (String username : usernames) {
            if (userRepository.existsByUsername(username)) {
                existingCount++;
                existingUsernames.add(username);
            } else {
                newCount++;
            }
        }

        result.put("total", usernames.size());
        result.put("existing", existingCount);
        result.put("new", newCount);
        result.put("existingUsernames", existingUsernames);

        return result;
    }

    @IntegrationTest
    @ExpectedResult(inputJson = "3", expectedJson = "3")
    @ExpectedResult(inputJson = "1", expectedJson = "1")
    @ExpectedResult(inputJson = "0", expectedJson = "0")
    public int countTotalUsers(int limit) {
        List<User> users = userRepository.findAll();
        return Math.min(users.size(), limit);
    }
}