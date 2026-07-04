package com.surense.customerhub.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surense.customerhub.auth.Credentials;
import com.surense.customerhub.auth.CredentialsRepository;
import com.surense.customerhub.auth.UserRole;
import com.surense.customerhub.auth.UserRoleRepository;
import com.surense.customerhub.auth.dto.LoginRequest;
import com.surense.customerhub.common.Role;
import com.surense.customerhub.customer.CustomerRepository;
import com.surense.customerhub.ticket.TicketRepository;
import com.surense.customerhub.profile.dto.UpdateProfileRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CredentialsRepository credentialsRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;

    private String agentToken;

    @BeforeEach
    void seedAndLogin() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ticketRepository.deleteAllInBatch();
            customerRepository.deleteAllInBatch();
            credentialsRepository.deleteAllInBatch();
            userRoleRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();

            User agent = userRepository.save(User.builder()
                    .email("agent@example.com")
                    .fullName("Agent Smith")
                    .build());
            credentialsRepository.save(Credentials.builder()
                    .user(agent).username("agent")
                    .passwordHash(passwordEncoder.encode("correct-password"))
                    .build());
            userRoleRepository.save(UserRole.builder().user(agent).role(Role.AGENT).build());

            // A second user, whose email will be the "taken" one in a duplicate test.
            User other = userRepository.save(User.builder()
                    .email("taken@example.com")
                    .fullName("Other User")
                    .build());
            credentialsRepository.save(Credentials.builder()
                    .user(other).username("other")
                    .passwordHash(passwordEncoder.encode("whatever"))
                    .build());
            userRoleRepository.save(UserRole.builder().user(other).role(Role.CUSTOMER).build());
        });
        agentToken = login("agent", "correct-password");
    }

    @Test
    void getMeWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_MISSING"));
    }

    @Test
    void getMeReturnsCurrentUsersProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("agent"))
                .andExpect(jsonPath("$.email").value("agent@example.com"))
                .andExpect(jsonPath("$.fullName").value("Agent Smith"))
                // internal id is never exposed
                .andExpect(jsonPath("$.id").doesNotExist())
                // roles belong to the security context (JWT), not the profile
                .andExpect(jsonPath("$.roles").doesNotExist());
    }

    @Test
    void patchMeUpdatesFullNameAndEmail() throws Exception {
        String body = objectMapper.writeValueAsString(
                new UpdateProfileRequest("Agent Updated", "agent.new@example.com"));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Agent Updated"))
                .andExpect(jsonPath("$.email").value("agent.new@example.com"))
                // username never changes via profile endpoint
                .andExpect(jsonPath("$.username").value("agent"));
    }

    @Test
    void patchMeWithInvalidEmailReturns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                new UpdateProfileRequest(null, "not-an-email"));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='email')]").exists());
    }

    @Test
    void patchMeWithTakenEmailReturns409() throws Exception {
        String body = objectMapper.writeValueAsString(
                new UpdateProfileRequest(null, "taken@example.com"));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT_DUPLICATE_EMAIL"));
    }

    @Test
    void patchMeWithoutTokenReturns401() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest(username, password));
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Objects.requireNonNull(objectMapper.readTree(response).get("accessToken").asText());
    }
}
