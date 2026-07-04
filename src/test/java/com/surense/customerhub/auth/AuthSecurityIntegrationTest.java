package com.surense.customerhub.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surense.customerhub.auth.dto.ChangePasswordRequest;
import com.surense.customerhub.auth.dto.LoginRequest;
import com.surense.customerhub.common.Role;
import com.surense.customerhub.customer.CustomerRepository;
import com.surense.customerhub.ticket.TicketRepository;
import com.surense.customerhub.user.User;
import com.surense.customerhub.user.UserRepository;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CredentialsRepository credentialsRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JwtProperties jwtProperties;

    @BeforeEach
    void seedUser() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ticketRepository.deleteAllInBatch();
            customerRepository.deleteAllInBatch();
            credentialsRepository.deleteAllInBatch();
            userRoleRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();

            User user = userRepository.save(User.builder()
                    .email("agent@example.com")
                    .fullName("Agent Smith")
                    .build());
            credentialsRepository.save(Credentials.builder()
                    .user(user)
                    .username("agent")
                    .passwordHash(passwordEncoder.encode("correct-password"))
                    .build());
            userRoleRepository.save(UserRole.builder().user(user).role(Role.AGENT).build());
        });
    }

    @Test
    void loginWithValidCredentialsReturnsBearerToken() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("agent", "correct-password"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void loginWithWrongPasswordReturns401WithClearMessage() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("agent", "wrong-password"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void loginWithBlankUsernameReturns400WithFieldError() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("", "any"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='username')]").exists());
    }

    @Test
    void loginWithMalformedBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("The request could not be understood."));
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTH_MISSING"));
    }

    @Test
    void protectedEndpointWithMalformedTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password")
                        .header("Authorization", "Bearer not-a-real-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordWithValidTokenAndWrongCurrentReturns400() throws Exception {
        String token = loginAndExtractToken("agent", "correct-password");
        String body = objectMapper.writeValueAsString(new ChangePasswordRequest("wrong-current", "brand-new-pw"));

        mockMvc.perform(post("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_CURRENT_INCORRECT"))
                .andExpect(jsonPath("$.message").value("Current password is incorrect."));
    }

    @Test
    void changePasswordSucceedsWithValidTokenAndCorrectCurrent() throws Exception {
        String token = loginAndExtractToken("agent", "correct-password");
        String body = objectMapper.writeValueAsString(new ChangePasswordRequest("correct-password", "brand-new-pw"));

        mockMvc.perform(post("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        Credentials refreshed = credentialsRepository.findByUsername("agent").orElseThrow();
        assertThat(passwordEncoder.matches("brand-new-pw", refreshed.getPasswordHash())).isTrue();
    }

    @Test
    void expiredTokenReturns401AuthMissing() throws Exception {
        String token = forgeToken("agent", List.of("AGENT"), jwtProperties.issuer(),
                Instant.now().minus(Duration.ofMinutes(2)),
                Instant.now().minus(Duration.ofMinutes(1)));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_MISSING"))
                .andExpect(jsonPath("$.message").value("Please sign in to continue."));
    }

    @Test
    void tamperedSignatureReturns401AuthMissing() throws Exception {
        String token = forgeToken("agent", List.of("AGENT"), jwtProperties.issuer(),
                Instant.now(), Instant.now().plus(Duration.ofMinutes(5)));
        String tampered = token.substring(0, token.length() - 1)
                + (token.charAt(token.length() - 1) == 'A' ? 'B' : 'A');

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_MISSING"));
    }

    @Test
    void wrongIssuerReturns401AuthMissing() throws Exception {
        String token = forgeToken("agent", List.of("AGENT"), "attacker",
                Instant.now(), Instant.now().plus(Duration.ofMinutes(5)));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_MISSING"));
    }

    @Test
    void missingRolesClaimReturns403OnRoleGatedEndpoint() throws Exception {
        String token = forgeToken("agent", null, jwtProperties.issuer(),
                Instant.now(), Instant.now().plus(Duration.ofMinutes(5)));

        mockMvc.perform(get("/api/v1/agents").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action."));
    }

    private String forgeToken(String subject, List<String> roles, String issuer,
                              Instant issuedAt, Instant expiresAt) {
        SecretKey key = new SecretKeySpec(
                jwtProperties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var builder = Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt));
        if (roles != null) {
            builder = builder.claim("roles", roles);
        }
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    private String loginAndExtractToken(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest(username, password));
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Objects.requireNonNull(objectMapper.readTree(response).get("accessToken").asText());
    }
}
