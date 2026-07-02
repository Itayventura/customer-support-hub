package com.surense.customerhub.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * UserDetails carried through the Spring Security authentication process at login time.
 * The internal {@code userId} is kept server-side only and never leaves the app.
 */
public class AppUserPrincipal extends User {

    private final Long userId;

    public AppUserPrincipal(
            Long userId,
            String username,
            String passwordHash,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(username, passwordHash, authorities);
        this.userId = userId;
    }

    public Long userId() {
        return userId;
    }
}
