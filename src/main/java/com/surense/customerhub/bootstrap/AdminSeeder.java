package com.surense.customerhub.bootstrap;

import com.surense.customerhub.auth.Credentials;
import com.surense.customerhub.auth.CredentialsRepository;
import com.surense.customerhub.auth.UserRole;
import com.surense.customerhub.auth.UserRoleRepository;
import com.surense.customerhub.common.Role;
import com.surense.customerhub.user.User;
import com.surense.customerhub.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.seed.admin", name = "enabled", havingValue = "true")
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final AdminSeedProperties properties;
    private final UserRepository userRepository;
    private final CredentialsRepository credentialsRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminSeeder(
            AdminSeedProperties properties,
            UserRepository userRepository,
            CredentialsRepository credentialsRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.credentialsRepository = credentialsRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!properties.enabled()) {
            log.info("Admin seeding disabled (app.seed.admin.enabled=false)");
            return;
        }

        validateProperties();

        if (userRepository.count() > 0) {
            log.info("Users table not empty — skipping admin seeding");
            return;
        }

        User user = userRepository.save(User.builder()
                .email(properties.email())
                .fullName(properties.fullName())
                .build());

        credentialsRepository.save(Credentials.builder()
                .user(user)
                .username(properties.username())
                .passwordHash(passwordEncoder.encode(properties.password()))
                .build());

        userRoleRepository.save(UserRole.builder()
                .user(user)
                .role(Role.ADMIN)
                .build());

        log.info("Seeded initial ADMIN user with username='{}'", properties.username());
    }

    private void validateProperties() {
        require(properties.username(), "app.seed.admin.username");
        require(properties.password(), "app.seed.admin.password");
        require(properties.email(), "app.seed.admin.email");
        require(properties.fullName(), "app.seed.admin.full-name");
    }

    private static void require(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Admin seeding is enabled but required property '" + key + "' is blank"
            );
        }
    }
}
