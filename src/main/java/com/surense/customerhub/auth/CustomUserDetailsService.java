package com.surense.customerhub.auth;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final CredentialsRepository credentialsRepository;
    private final UserRoleRepository userRoleRepository;

    public CustomUserDetailsService(
            CredentialsRepository credentialsRepository,
            UserRoleRepository userRoleRepository
    ) {
        this.credentialsRepository = credentialsRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Credentials credentials = credentialsRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<SimpleGrantedAuthority> authorities = userRoleRepository
                .findAllByUserId(credentials.getUserId())
                .stream()
                .map(ur -> new SimpleGrantedAuthority("ROLE_" + ur.getRole().name()))
                .toList();

        return new AppUserPrincipal(
                credentials.getUserId(),
                credentials.getUsername(),
                credentials.getPasswordHash(),
                authorities
        );
    }
}
