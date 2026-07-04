package com.surense.customerhub.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surense.customerhub.auth.Credentials;
import com.surense.customerhub.auth.CredentialsRepository;
import com.surense.customerhub.auth.UserRole;
import com.surense.customerhub.auth.UserRoleRepository;
import com.surense.customerhub.auth.dto.LoginRequest;
import com.surense.customerhub.common.Role;
import com.surense.customerhub.customer.Customer;
import com.surense.customerhub.customer.CustomerRepository;
import com.surense.customerhub.ticket.dto.CreateTicketRequest;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TicketSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CredentialsRepository credentialsRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;

    private String adminToken;
    private String agentAToken;
    private String agentBToken;
    private String custAToken;
    private String custBToken;

    private UUID ticketOfCustAId;
    private UUID ticketOfCustBId;

    @BeforeEach
    void seedMatrix() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ticketRepository.deleteAllInBatch();
            customerRepository.deleteAllInBatch();
            credentialsRepository.deleteAllInBatch();
            userRoleRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();

            seedUser("admin", "admin@ex.com", "Admin", Role.ADMIN, "pw-admin-123");
            User agentA = seedUser("agentA", "agA@ex.com", "Agent A", Role.AGENT, "pw-agentA-123");
            User agentB = seedUser("agentB", "agB@ex.com", "Agent B", Role.AGENT, "pw-agentB-123");
            User custA = seedUser("custA", "cA@ex.com", "Cust A", Role.CUSTOMER, "pw-custA-123");
            User custB = seedUser("custB", "cB@ex.com", "Cust B", Role.CUSTOMER, "pw-custB-123");
            Customer customerA = customerRepository.save(Customer.builder().user(custA).agent(agentA).build());
            Customer customerB = customerRepository.save(Customer.builder().user(custB).agent(agentB).build());

            Ticket ta = ticketRepository.save(Ticket.builder()
                    .title("A's ticket").description("A body")
                    .status(TicketStatus.OPEN).customer(customerA).build());
            Ticket tb = ticketRepository.save(Ticket.builder()
                    .title("B's ticket").description("B body")
                    .status(TicketStatus.OPEN).customer(customerB).build());

            ticketOfCustAId = ta.getExternalId();
            ticketOfCustBId = tb.getExternalId();
        });
        adminToken = login("admin", "pw-admin-123");
        agentAToken = login("agentA", "pw-agentA-123");
        agentBToken = login("agentB", "pw-agentB-123");
        custAToken = login("custA", "pw-custA-123");
        custBToken = login("custB", "pw-custB-123");
    }

    private User seedUser(String username, String email, String fullName, Role role, String plainPw) {
        User user = userRepository.save(User.builder().email(email).fullName(fullName).build());
        credentialsRepository.save(Credentials.builder()
                .user(user).username(username).passwordHash(passwordEncoder.encode(plainPw)).build());
        userRoleRepository.save(UserRole.builder().user(user).role(role).build());
        return user;
    }

    // -------- POST /tickets --------

    @Test
    void customerCanCreateTicket() throws Exception {
        String body = objectMapper.writeValueAsString(
                new CreateTicketRequest("New issue", "Details here"));

        mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", "Bearer " + custAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.customer.username").value("custA"));
    }

    @Test
    void agentCannotCreateTicketReturns403() throws Exception {
        String body = objectMapper.writeValueAsString(
                new CreateTicketRequest("agent-made", "no way"));

        mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", "Bearer " + agentAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminCannotCreateTicketReturns403() throws Exception {
        String body = objectMapper.writeValueAsString(
                new CreateTicketRequest("admin-made", "no way"));

        mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTicketWithoutTokenReturns401() throws Exception {
        String body = objectMapper.writeValueAsString(
                new CreateTicketRequest("nope", "nope"));

        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createTicketWithBlankTitleReturns400() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateTicketRequest("", "body"));

        mockMvc.perform(post("/api/v1/tickets")
                        .header("Authorization", "Bearer " + custAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // -------- GET /tickets --------

    @Test
    void customerListsOnlyOwnTickets() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").header("Authorization", "Bearer " + custAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ticketOfCustAId.toString()));
    }

    @Test
    void agentListsOnlyOwnCustomersTickets() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").header("Authorization", "Bearer " + agentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customer.username").value("custA"));
    }

    @Test
    void adminListsAllTickets() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void adminCanFilterByStatus() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").param("status", "CLOSED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/tickets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listWithInvalidStatusEnumReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").param("status", "BOGUS")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='status')]").exists());
    }

    // -------- GET /tickets/{id} --------

    @Test
    void customerGetsOwnTicket() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/" + ticketOfCustAId)
                        .header("Authorization", "Bearer " + custAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketOfCustAId.toString()));
    }

    @Test
    void customerAGetsCustomerBsTicketReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/" + ticketOfCustBId)
                        .header("Authorization", "Bearer " + custAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void agentGetsOwnCustomersTicket() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/" + ticketOfCustAId)
                        .header("Authorization", "Bearer " + agentAToken))
                .andExpect(status().isOk());
    }

    @Test
    void agentBGetsAgentAsCustomerTicketReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/" + ticketOfCustAId)
                        .header("Authorization", "Bearer " + agentBToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void adminGetsAnyTicket() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/" + ticketOfCustAId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/tickets/" + ticketOfCustBId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void getNonExistentTicketReturns404() throws Exception {
        UUID randomId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/tickets/" + randomId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void getWithMalformedUuidReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/not-a-uuid")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='id')]").exists());
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
