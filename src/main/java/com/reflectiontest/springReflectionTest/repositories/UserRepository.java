package com.reflectiontest.springReflectionTest.repositories;

import com.reflectiontest.springReflectionTest.models.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    /**
     * Find a user by username
     */
    Optional<User> findByUsername(String username);

    /**
     * Check if a user exists by username
     */
    boolean existsByUsername(String username);

    /**
     * Find users by role
     */
    List<User> findByRole(String role);

    /**
     * Find users with email matching a pattern
     */
    List<User> findByEmailContaining(String emailDomain);

    /**
     * Custom query to find users with a specific role and email domain
     */
    @Query("{'role': ?0, 'email': {$regex: ?1}}")
    List<User> findUsersByRoleAndEmailDomain(String role, String emailDomain);

    /**
     * Delete a user by username
     */
    void deleteByUsername(String username);
}
