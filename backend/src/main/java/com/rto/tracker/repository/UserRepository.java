package com.rto.tracker.repository;

import com.rto.tracker.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByApiKeyHashAndActiveTrue(String apiKeyHash);

    Optional<User> findByEmail(String email);

    long countByActiveTrue();
}
