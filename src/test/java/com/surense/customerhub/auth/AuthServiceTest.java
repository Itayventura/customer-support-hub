package com.surense.customerhub.auth;

import com.surense.customerhub.auth.dto.ChangePasswordRequest;
import com.surense.customerhub.auth.dto.LoginRequest;
import com.surense.customerhub.auth.dto.TokenResponse;
import com.surense.customerhub.common.exception.ApiException;
import com.surense.customerhub.common.exception.ErrorCode;
import com.surense.customerhub.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private CredentialsRepository credentialsRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private Credentials aliceCredentials;

    @BeforeEach
    void setUp() {
        User alice = User.builder().id(7L).email("alice@example.com").fullName("Alice").build();
        aliceCredentials = Credentials.builder()
                .userId(7L)
                .user(alice)
                .username("alice")
                .passwordHash("hashed-current")
                .build();
    }

    @Test
    void loginReturnsBearerTokenWithConfiguredExpiry() {
        Authentication auth = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(auth.getAuthorities()).thenAnswer(inv -> List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
        when(jwtService.issueToken("alice", List.of("AGENT")))
                .thenReturn(new JwtService.IssuedToken("signed-jwt", Instant.now().plusSeconds(3600), 3600L));

        TokenResponse response = authService.login(new LoginRequest("alice", "correct-password"));

        assertThat(response.accessToken()).isEqualTo("signed-jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
    }

    @Test
    void loginPropagatesBadCredentials() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        when(currentUserService.currentCredentials()).thenReturn(aliceCredentials);
        when(passwordEncoder.matches("wrong-current", "hashed-current")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(new ChangePasswordRequest("wrong-current", "brand-new-pw")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Current password is incorrect")
                .extracting(t -> ((ApiException) t).getCode())
                .isEqualTo(ErrorCode.PASSWORD_CURRENT_INCORRECT);

        verify(credentialsRepository, never()).save(any());
    }

    @Test
    void changePasswordRejectsReusingSamePassword() {
        when(currentUserService.currentCredentials()).thenReturn(aliceCredentials);
        when(passwordEncoder.matches("same-pw", "hashed-current")).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(new ChangePasswordRequest("same-pw", "same-pw")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("must be different")
                .extracting(t -> ((ApiException) t).getCode())
                .isEqualTo(ErrorCode.PASSWORD_SAME_AS_CURRENT);

        verify(credentialsRepository, never()).save(any());
    }

    @Test
    void changePasswordUpdatesHashWhenCurrentIsCorrectAndNewIsDifferent() {
        when(currentUserService.currentCredentials()).thenReturn(aliceCredentials);
        when(passwordEncoder.matches("current-pw", "hashed-current")).thenReturn(true);
        when(passwordEncoder.matches("brand-new-pw", "hashed-current")).thenReturn(false);
        when(passwordEncoder.encode("brand-new-pw")).thenReturn("hashed-new");

        authService.changePassword(new ChangePasswordRequest("current-pw", "brand-new-pw"));

        assertThat(aliceCredentials.getPasswordHash()).isEqualTo("hashed-new");
        verify(credentialsRepository).save(aliceCredentials);
    }
}
