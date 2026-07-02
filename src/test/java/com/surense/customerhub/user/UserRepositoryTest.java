package com.surense.customerhub.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndLoadsUser() {
        User saved = userRepository.save(User.builder()
                .email("alice@example.com")
                .fullName("Alice")
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<User> byEmail = userRepository.findByEmail("alice@example.com");
        assertThat(byEmail).isPresent();
        assertThat(byEmail.get().getFullName()).isEqualTo("Alice");
    }

    @Test
    void rejectsDuplicateEmail() {
        userRepository.saveAndFlush(User.builder()
                .email("dup@example.com")
                .fullName("First")
                .build());

        assertThatThrownBy(() -> userRepository.saveAndFlush(User.builder()
                .email("dup@example.com")
                .fullName("Second")
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
