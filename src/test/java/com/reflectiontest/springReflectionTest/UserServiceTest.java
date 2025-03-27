package com.reflectiontest.springReflectionTest;

import com.reflectiontest.springReflectionTest.examples.UserService;
import com.reflectiontest.springReflectionTest.models.User;
import com.reflectiontest.springReflectionTest.repositories.AuthenticationRepository;
import com.reflectiontest.springReflectionTest.repositories.TokenRepository;
import com.reflectiontest.springReflectionTest.repositories.UserRepository;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationRepository authRepository;

    @Mock
    private TokenRepository tokenRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userService = new UserService(userRepository, authRepository, tokenRepository);
    }

    @Test
    @Order(1)
    void testRegisterUser() {
        User user = new User("johndoe", "johndoe@example.com", "password123", "USER");
        when(userRepository.existsByUsername("johndoe")).thenReturn(false);

        assertTrue(userService.registerUser(user));
        assertFalse(userService.registerUser(new User("", "user@example.com", "password123", "USER")));
        assertFalse(userService.registerUser(new User("johndoe", "user@example.com", "", "USER")));
        assertFalse(userService.registerUser(new User("admin", "admin@example.com", "admin", "ADMIN")));
        assertFalse(userService.registerUser(new User(null, "user@example.com", "password123", "USER")));
    }

    @Test
    @Order(2)
    void testAuthenticateUser() {
        User validUser = new User("johndoe", "johndoe@example.com", "password123", "USER");
        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(validUser));
        when(authRepository.isAccountLocked("johndoe")).thenReturn(false);

        Map<String, Object> success = userService.authenticateUser(validUser, "192.168.1.1");
        assertTrue((Boolean) success.get("success"));
        assertEquals("Login successful", success.get("message"));

        User wrongPassword = new User("johndoe", "johndoe@example.com", "wrongpassword", "USER");
        Map<String, Object> fail1 = userService.authenticateUser(wrongPassword, "192.168.1.1");
        assertFalse((Boolean) fail1.get("success"));

        when(userRepository.findByUsername("doesnotexist")).thenReturn(Optional.empty());
        Map<String, Object> fail2 = userService.authenticateUser(new User("doesnotexist", "na@example.com", "password123", "USER"), "192.168.1.1");
        assertFalse((Boolean) fail2.get("success"));

        when(authRepository.isAccountLocked("lockedUser")).thenReturn(true);
        Map<String, Object> fail3 = userService.authenticateUser(new User("lockedUser", "locked@example.com", "password123", "USER"), "192.168.1.1");
        assertEquals("Account is locked due to too many failed attempts", fail3.get("message"));
    }

    @Test
    @Order(3)
    void testChangePassword() {
        User user = new User("johndoe", "johndoe@example.com", "password123", "USER");
        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(user));

        assertTrue(userService.changePassword("johndoe", "password123", "newpassword456"));
        assertFalse(userService.changePassword("johndoe", "wrongpassword", "newpassword456"));
        assertFalse(userService.changePassword("johndoe", "password123", "weak"));
        when(userRepository.findByUsername("doesnotexist")).thenReturn(Optional.empty());
        assertFalse(userService.changePassword("doesnotexist", "password123", "newpassword456"));
    }

    @Test
    @Order(4)
    void testIsUserRegistered() {
        when(userRepository.existsByUsername("johndoe")).thenReturn(true);
        assertTrue(userService.isUserRegistered("johndoe"));
        when(userRepository.existsByUsername("doesnotexist")).thenReturn(false);
        assertFalse(userService.isUserRegistered("doesnotexist"));
        assertFalse(userService.isUserRegistered(null));
    }

    @Test
    @Order(5)
    void testIsPasswordStrong() {
        assertTrue(userService.isPasswordStrong("StrongPass1!"));
        assertFalse(userService.isPasswordStrong("weakpass"));
        assertFalse(userService.isPasswordStrong("12345678"));
        assertFalse(userService.isPasswordStrong("NoSpecial123"));
        assertFalse(userService.isPasswordStrong("!@#$%^&*()"));
        assertTrue(userService.isPasswordStrong("aB1!aB1!aB1!aB1!aB1!"));
        assertFalse(userService.isPasswordStrong(""));
        assertFalse(userService.isPasswordStrong(null));
    }

    @Test
    @Order(6)
    void testAnalyzeUsernames() {
        when(userRepository.existsByUsername("johndoe")).thenReturn(true);
        when(userRepository.existsByUsername("janedoe")).thenReturn(false);
        when(userRepository.existsByUsername("bobsmith")).thenReturn(false);

        Map<String, Integer> result = userService.analyzeUsernames(List.of("johndoe", "janedoe", "bobsmith"));
        assertEquals(3, result.get("total"));
        assertEquals(1, result.get("existing"));
        assertEquals(2, result.get("new"));
    }

    @Test
    @Order(7)
    void testGetUserEmail() {
        when(userRepository.findByUsername("johndoe"))
                .thenReturn(Optional.of(new User("johndoe", "johndoe@example.com", "pass", "USER")));
        assertEquals("johndoe@example.com", userService.getUserEmail("johndoe"));

        when(userRepository.findByUsername("doesnotexist")).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> userService.getUserEmail("doesnotexist"));
        assertThrows(IllegalArgumentException.class, () -> userService.getUserEmail(null));
    }

    @Test
    @Order(8)
    void testGetUserInfo() {
        User user = new User("johndoe", "johndoe@example.com", "password123", "USER");
        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(user));

        Optional<Map<String, String>> info = userService.getUserInfo("johndoe");
        assertTrue(info.isPresent());
        assertEquals("johndoe", info.get().get("username"));
        assertEquals("masked", info.get().get("password"));

        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        assertFalse(userService.getUserInfo("unknown").isPresent());
        assertFalse(userService.getUserInfo(null).isPresent());
    }

    @Test
    @Order(9)
    void testGeneratePasswordResetToken() {
        when(userRepository.existsByUsername("johndoe")).thenReturn(true);
        when(tokenRepository.generateResetToken("johndoe")).thenReturn("token123");

        assertEquals("token123", userService.generatePasswordResetToken("johndoe"));
        when(userRepository.existsByUsername("unknown")).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> userService.generatePasswordResetToken("unknown"));
    }

    @Test
    @Order(10)
    void testResetPassword() {
        User user = new User("johndoe", "johndoe@example.com", "oldpass", "USER");
        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(user));
        when(tokenRepository.validateResetToken("johndoe", "token123")).thenReturn(true);
        assertTrue(userService.resetPassword("johndoe", "token123", "newSecurePass1!"));

        when(tokenRepository.validateResetToken("johndoe", "wrongtoken")).thenReturn(false);
        assertFalse(userService.resetPassword("johndoe", "wrongtoken", "newSecurePass1!"));

        assertFalse(userService.resetPassword("johndoe", "token123", "weak"));
    }
}
