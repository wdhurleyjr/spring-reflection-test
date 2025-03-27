package com.reflectiontest.springReflectionTest.repositories;

import com.reflectiontest.springReflectionTest.annotations.MockReturn;
import com.reflectiontest.springReflectionTest.annotations.MockReturns;
import com.reflectiontest.springReflectionTest.models.User;
import java.util.Optional;

public interface UserRepository {
    @MockReturns({
            @MockReturn(inputJson = "\"johndoe\"", returnJson = "true"),
            @MockReturn(inputJson = "\"admin\"", returnJson = "true"),
            @MockReturn(inputJson = "\"newuser\"", returnJson = "false", isDefault = true)
    })
    boolean existsByUsername(String username);

    @MockReturns({
            @MockReturn(inputJson = "\"johndoe\"",
                    returnJson = "{\"username\":\"johndoe\",\"email\":\"johndoe@example.com\",\"role\":\"user\"}",
                    isDefault = false),
            @MockReturn(inputJson = "\"admin\"",
                    returnJson = "{\"username\":\"admin\",\"email\":\"admin@system.com\",\"role\":\"admin\"}",
                    isDefault = false),
            @MockReturn(inputJson = "",
                    returnJson = "null",
                    isDefault = true)
    })
    Optional<User> findByUsername(String username);

    @MockReturns({
            @MockReturn(inputJson = "{\"username\":\"johndoe\",\"email\":\"johndoe@example.com\",\"password\":\"password123\",\"role\":\"user\"}",
                    returnJson = "{\"username\":\"johndoe\",\"email\":\"johndoe@example.com\",\"password\":\"password123\",\"role\":\"user\"}")
    })
    User save(User user);

    @MockReturns({
            @MockReturn(inputJson = "\"johndoe\"", returnJson = "")
    })
    void deleteByUsername(String username);
}
