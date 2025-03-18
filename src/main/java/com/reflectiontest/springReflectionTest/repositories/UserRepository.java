package com.reflectiontest.springReflectionTest.repositories;

import com.reflectiontest.springReflectionTest.models.User;
import java.util.Optional;

public interface UserRepository {

    boolean existsByUsername(String username);

    Optional<User> findByUsername(String username);

    User save(User user);

    void deleteByUsername(String username);
}

