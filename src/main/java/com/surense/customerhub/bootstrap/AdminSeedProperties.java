package com.surense.customerhub.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.seed.admin")
public record AdminSeedProperties(
        boolean enabled,
        String username,
        String password,
        String email,
        String fullName
) {
}
