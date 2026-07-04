package com.surense.customerhub.auth;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CredentialsRepository extends JpaRepository<Credentials, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<Credentials> findByUsername(String username);

    boolean existsByUsername(String username);
}
