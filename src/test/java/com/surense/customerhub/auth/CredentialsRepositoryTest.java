package com.surense.customerhub.auth;

import com.surense.customerhub.common.Role;
import com.surense.customerhub.user.User;
import com.surense.customerhub.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CredentialsRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CredentialsRepository credentialsRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void credentialsShareIdWithUserViaMapsId() {
        User user = userRepository.save(User.builder()
                .email("agent@example.com")
                .fullName("Agent Smith")
                .build());

        Credentials credentials = credentialsRepository.save(Credentials.builder()
                .user(user)
                .username("agent.smith")
                .passwordHash("$2a$10$fake-hash")
                .build());

        assertThat(credentials.getUserId()).isEqualTo(user.getId());

        Optional<Credentials> byUsername = credentialsRepository.findByUsername("agent.smith");
        assertThat(byUsername).isPresent();
        assertThat(byUsername.get().getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void userRolesAreQueryableByUser() {
        User user = userRepository.save(User.builder()
                .email("admin@example.com")
                .fullName("Admin")
                .build());

        userRoleRepository.save(UserRole.builder().user(user).role(Role.ADMIN).build());

        List<UserRole> roles = userRoleRepository.findAllByUserId(user.getId());
        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).getRole()).isEqualTo(Role.ADMIN);
        assertThat(userRoleRepository.existsByUserAndRole(user, Role.ADMIN)).isTrue();
        assertThat(userRoleRepository.existsByUserAndRole(user, Role.AGENT)).isFalse();
    }
}
