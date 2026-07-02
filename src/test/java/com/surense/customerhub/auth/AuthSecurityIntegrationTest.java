package com.surense.customerhub.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surense.customerhub.auth.dto.ChangePasswordRequest;
import com.surense.customerhub.auth.dto.LoginRequest;
import com.surense.customerhub.common.Role;
import com.surense.customerhub.user.User;
import com.surense.customerhub.user.UserRepository;
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

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void seedUser() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
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
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
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
                .andExpect(jsonPath("$.message").value("Malformed request body"));
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
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
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
