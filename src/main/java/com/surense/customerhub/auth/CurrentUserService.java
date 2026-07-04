package com.surense.customerhub.auth;

import com.surense.customerhub.common.Role;
import com.surense.customerhub.common.exception.ApiException;
import com.surense.customerhub.common.exception.ErrorCode;
import com.surense.customerhub.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the currently authenticated user by looking up their username from the JWT subject.
 * Internal {@code User.id} is never present in the JWT — this per-request lookup preserves
 * the "internal id never exposed" rule.
 */
@Service
public class CurrentUserService {

    private final CredentialsRepository credentialsRepository;

    public CurrentUserService(CredentialsRepository credentialsRepository) {
        this.credentialsRepository = credentialsRepository;
    }

    public String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new ApiException(ErrorCode.AUTH_MISSING);
        }
        return auth.getName();
    }

    @Transactional(readOnly = true)
    public User currentUser() {
        String username = currentUsername();
        return credentialsRepository.findByUsername(username)
                .map(Credentials::getUser)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_MISSING));
    }

    @Transactional(readOnly = true)
    public Credentials currentCredentials() {
        String username = currentUsername();
        return credentialsRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_MISSING));
    }

    /**
     * True if the current caller carries the given role as a Spring Security authority
     * ({@code "ROLE_" + role.name()}).
     */
    public boolean hasRole(Role role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        String needle = "ROLE_" + role.name();
        return auth.getAuthorities().stream().anyMatch(a -> needle.equals(a.getAuthority()));
    }
}
