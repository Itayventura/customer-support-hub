package com.surense.customerhub.auth;

import com.surense.customerhub.auth.dto.ChangePasswordRequest;
import com.surense.customerhub.auth.dto.LoginRequest;
import com.surense.customerhub.auth.dto.TokenResponse;
import com.surense.customerhub.common.exception.ApiException;
import com.surense.customerhub.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CredentialsRepository credentialsRepository;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            CredentialsRepository credentialsRepository,
            CurrentUserService currentUserService,
            PasswordEncoder passwordEncoder,
            PlatformTransactionManager transactionManager
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.credentialsRepository = credentialsRepository;
        this.currentUserService = currentUserService;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public TokenResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .toList();

        JwtService.IssuedToken token = jwtService.issueToken(request.username(), roles);
        log.info("Login success username={} roles={}", request.username(), roles);
        return TokenResponse.bearer(token.token(), token.expiresInSeconds());
    }

    public void changePassword(ChangePasswordRequest request) {
        Credentials credentials = currentUserService.currentCredentials();

        if (!passwordEncoder.matches(request.currentPassword(), credentials.getPasswordHash())) {
            throw new ApiException(ErrorCode.PASSWORD_CURRENT_INCORRECT);
        }
        if (passwordEncoder.matches(request.newPassword(), credentials.getPasswordHash())) {
            throw new ApiException(ErrorCode.PASSWORD_SAME_AS_CURRENT);
        }

        credentials.setPasswordHash(passwordEncoder.encode(request.newPassword()));

        transactionTemplate.executeWithoutResult(status -> credentialsRepository.save(credentials));
        log.info("Password changed username={}", credentials.getUsername());
    }
}
