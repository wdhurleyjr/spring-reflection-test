package com.reflectiontest.springReflectionTest;

import com.reflectiontest.springReflectionTest.models.User;
import com.reflectiontest.springReflectionTest.repositories.UserRepository;
import com.reflectiontest.springReflectionTest.services.UserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User johndoe;
    private User janedoe;
    private User adminUser;

    @BeforeEach
    void setUp() {
        johndoe = new User("johndoe", "johndoe@example.com", "password123", "USER");
        janedoe = new User("janedoe", "janedoe@example.com", "password456", "USER");
        adminUser = new User("admin", "admin@system.com", "adminpass", "ADMIN");
    }

    @Test
    @Order(1)
    void testRegisterUser() {
        // Scenario 1: Valid user registration
        when(userRepository.existsByUsername("johndoe")).thenReturn(false);
        assertTrue(userService.registerUser(johndoe));

        // Scenario 2: Empty username
        assertFalse(userService.registerUser(new User("", "test@example.com", "password123", "USER")));

        // Scenario 3: Empty password
        assertFalse(userService.registerUser(new User("testuser", "test@example.com", "", "USER")));

        // Scenario 4: Admin username not allowed
        assertFalse(userService.registerUser(new User("admin", "admin@example.com", "adminpass", "ADMIN")));

        // Scenario 5: Null user
        assertFalse(userService.registerUser(null));

        // Scenario 6: Invalid email
        assertFalse(userService.registerUser(new User("testuser", "invalid-email", "password123", "USER")));
    }

    @Test
    @Order(2)
    void testCountUsersByRole() {
        // Scenario 1: Users with specific role
        when(userRepository.findByRole("USER")).thenReturn(Arrays.asList(johndoe, janedoe));
        assertEquals(2, userService.countUsersByRole("USER"));

        // Scenario 2: No users with role
        when(userRepository.findByRole("ADMIN")).thenReturn(Collections.emptyList());
        assertEquals(0, userService.countUsersByRole("ADMIN"));
    }

    @Test
    @Order(3)
    void testCountUsersByEmailDomain() {
        // Scenario 1: Users with specific email domain
        when(userRepository.findByEmailContaining("example.com"))
                .thenReturn(Arrays.asList(johndoe, janedoe));
        assertEquals(2, userService.countUsersByEmailDomain("example.com"));

        // Scenario 2: No users with email domain
        when(userRepository.findByEmailContaining("test.com"))
                .thenReturn(Collections.emptyList());
        assertEquals(0, userService.countUsersByEmailDomain("test.com"));
    }

    @Test
    @Order(4)
    void testCountUsersByRoleAndEmailDomain() {
        // Scenario 1: Users matching role and email domain
        when(userRepository.findUsersByRoleAndEmailDomain("USER", ".*example\\.com"))
                .thenReturn(Arrays.asList(johndoe, janedoe));
        assertEquals(2, userService.countUsersByRoleAndEmailDomain("USER", "example.com"));

        // Scenario 2: No users matching
        when(userRepository.findUsersByRoleAndEmailDomain("ADMIN", ".*admin\\.com"))
                .thenReturn(Collections.emptyList());
        assertEquals(0, userService.countUsersByRoleAndEmailDomain("ADMIN", "admin.com"));
    }

    @Test
    @Order(5)
    void testIsPasswordStrong() {
        // Strong password scenarios
        assertTrue(userService.isPasswordStrong("StrongPass1!"));
        assertTrue(userService.isPasswordStrong("aB1!aB1!aB1!aB1!aB1!"));

        // Weak password scenarios
        assertFalse(userService.isPasswordStrong("weakpass"));
        assertFalse(userService.isPasswordStrong("12345678"));
        assertFalse(userService.isPasswordStrong("NoSpecial123"));
        assertFalse(userService.isPasswordStrong("!@#$%^&*()"));
        assertFalse(userService.isPasswordStrong(""));
        assertFalse(userService.isPasswordStrong(null));
    }

    @Test
    @Order(6)
    void testAnalyzeUsernames() {
        // Scenario 1: Mixed existing and new usernames
        when(userRepository.existsByUsername("johndoe")).thenReturn(true);
        when(userRepository.existsByUsername("janedoe")).thenReturn(false);
        when(userRepository.existsByUsername("bobsmith")).thenReturn(false);

        List<String> usernames = Arrays.asList("johndoe", "janedoe", "bobsmith");
        Map<String, Object> result = userService.analyzeUsernames(usernames);

        assertEquals(3, result.get("total"));
        assertEquals(1, result.get("existing"));
        assertEquals(2, result.get("new"));
        assertEquals(Collections.singletonList("johndoe"), result.get("existingUsernames"));

        // Scenario 2: Empty list
        result = userService.analyzeUsernames(Collections.emptyList());
        assertEquals(0, result.get("total"));
        assertEquals(0, result.get("existing"));
        assertEquals(0, result.get("new"));
        assertTrue(((List<?>) result.get("existingUsernames")).isEmpty());
    }

    @Test
    @Order(7)
    void testCountTotalUsers() {
        // Scenario 1: Multiple users
        List<User> allUsers = Arrays.asList(johndoe, janedoe, adminUser);
        when(userRepository.findAll()).thenReturn(allUsers);

        assertEquals(3, userService.countTotalUsers(3));

        // Scenario 2: Limit less than total users
        assertEquals(2, userService.countTotalUsers(2));

        // Scenario 3: Zero limit
        assertEquals(0, userService.countTotalUsers(0));
    }
}