package com.surense.customerhub.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surense.customerhub.agent.dto.CreateAgentRequest;
import com.surense.customerhub.auth.Credentials;
import com.surense.customerhub.auth.CredentialsRepository;
import com.surense.customerhub.auth.UserRole;
import com.surense.customerhub.auth.UserRoleRepository;
import com.surense.customerhub.auth.dto.LoginRequest;
import com.surense.customerhub.common.Role;
import com.surense.customerhub.customer.dto.CreateCustomerRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RoleSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CredentialsRepository credentialsRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;

    private String adminToken;
    private String agentAToken;
    private String agentBToken;
    private String customerToken;

    @BeforeEach
    void seedRoleMatrix() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            customerRepository.deleteAllInBatch();
            credentialsRepository.deleteAllInBatch();
            userRoleRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();

            seedUser("admin", "admin@ex.com", "Admin One", Role.ADMIN, "pw-admin-123");
            User agentA = seedUser("agentA", "agentA@ex.com", "Agent A", Role.AGENT, "pw-agentA-123");
            User agentB = seedUser("agentB", "agentB@ex.com", "Agent B", Role.AGENT, "pw-agentB-123");
            User custOfA = seedUser("custOfA", "custA@ex.com", "Customer Of A", Role.CUSTOMER, "pw-cust-123");
            User custOfB = seedUser("custOfB", "custB@ex.com", "Customer Of B", Role.CUSTOMER, "pw-cust-123");
            customerRepository.save(Customer.builder().user(custOfA).agent(agentA).build());
            customerRepository.save(Customer.builder().user(custOfB).agent(agentB).build());
        });
        adminToken = login("admin", "pw-admin-123");
        agentAToken = login("agentA", "pw-agentA-123");
        agentBToken = login("agentB", "pw-agentB-123");
        customerToken = login("custOfA", "pw-cust-123");
    }

    private User seedUser(String username, String email, String fullName, Role role, String plainPw) {
        User user = userRepository.save(User.builder().email(email).fullName(fullName).build());
        credentialsRepository.save(Credentials.builder()
                .user(user).username(username).passwordHash(passwordEncoder.encode(plainPw)).build());
        userRoleRepository.save(UserRole.builder().user(user).role(role).build());
        return user;
    }

    // -------- POST /agents (ADMIN only) --------

    @Test
    void adminCanCreateAgent() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateAgentRequest(
                "newagent", "s3cret-pw", "newagent@ex.com", "New Agent"));

        mockMvc.perform(post("/api/v1/agents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newagent"))
                .andExpect(jsonPath("$.email").value("newagent@ex.com"))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void agentCannotCreateAgentReturns403() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateAgentRequest(
                "sneakyagent", "s3cret-pw", "sneak@ex.com", "Sneaky"));

        mockMvc.perform(post("/api/v1/agents")
                        .header("Authorization", "Bearer " + agentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void customerCannotCreateAgentReturns403() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateAgentRequest(
                "sneakyagent", "s3cret-pw", "sneak@ex.com", "Sneaky"));

        mockMvc.perform(post("/api/v1/agents")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAgentWithDuplicateUsernameReturns409() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateAgentRequest(
                "agentA", "s3cret-pw", "different@ex.com", "Different Person"));

        mockMvc.perform(post("/api/v1/agents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT_DUPLICATE_USERNAME"));
    }

    // -------- GET /agents --------

    @Test
    void adminCanListAgents() throws Exception {
        mockMvc.perform(get("/api/v1/agents").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.username=='agentA')]").exists())
                .andExpect(jsonPath("$[?(@.username=='agentB')]").exists());
    }

    @Test
    void agentCannotListAgents() throws Exception {
        mockMvc.perform(get("/api/v1/agents").header("Authorization", "Bearer " + agentAToken))
                .andExpect(status().isForbidden());
    }

    // -------- POST /customers (AGENT only) --------

    @Test
    void agentCanCreateCustomer() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateCustomerRequest(
                "newcust", "s3cret-pw", "newcust@ex.com", "New Customer"));

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + agentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newcust"))
                .andExpect(jsonPath("$.agent.username").value("agentA"))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void adminCannotCreateCustomerReturns403() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateCustomerRequest(
                "adminmade", "s3cret-pw", "am@ex.com", "Admin Made"));

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotCreateCustomerReturns403() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateCustomerRequest(
                "custmade", "s3cret-pw", "cm@ex.com", "Cust Made"));

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // -------- GET /customers --------

    @Test
    void adminSeesAllCustomers() throws Exception {
        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void agentSeesOnlyOwnCustomers() throws Exception {
        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + agentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("custOfA"))
                .andExpect(jsonPath("$[0].agent.username").value("agentA"));
    }

    @Test
    void agentBDoesNotSeeAgentAsCustomers() throws Exception {
        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + agentBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("custOfB"));
    }

    @Test
    void customerCannotListCustomers() throws Exception {
        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
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
