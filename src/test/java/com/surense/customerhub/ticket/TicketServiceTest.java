package com.surense.customerhub.ticket;

import com.surense.customerhub.auth.Credentials;
import com.surense.customerhub.auth.CredentialsRepository;
import com.surense.customerhub.auth.CurrentUserService;
import com.surense.customerhub.common.Role;
import com.surense.customerhub.common.exception.ApiException;
import com.surense.customerhub.common.exception.ErrorCode;
import com.surense.customerhub.customer.Customer;
import com.surense.customerhub.customer.CustomerRepository;
import com.surense.customerhub.ticket.dto.CreateTicketRequest;
import com.surense.customerhub.ticket.dto.TicketResponse;
import com.surense.customerhub.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock private CurrentUserService currentUserService;
    @Mock private CustomerRepository customerRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private CredentialsRepository credentialsRepository;

    @InjectMocks private TicketService ticketService;

    private User agent;
    private User customerUser;
    private Customer customer;
    private Credentials customerCredentials;

    @BeforeEach
    void setUp() {
        agent = User.builder().id(1L).email("agent@ex.com").fullName("Agent").build();
        customerUser = User.builder().id(2L).email("cust@ex.com").fullName("Customer One").build();
        customer = Customer.builder().userId(2L).user(customerUser).agent(agent).build();
        customerCredentials = Credentials.builder().userId(2L).user(customerUser).username("cust1").passwordHash("h").build();
    }

    @Test
    void createTicketPersistsWithOpenStatusForCurrentCustomer() {
        when(currentUserService.currentUser()).thenReturn(customerUser);
        when(customerRepository.findById(2L)).thenReturn(Optional.of(customer));
        Ticket saved = Ticket.builder()
                .id(100L)
                .externalId(UUID.randomUUID())
                .title("Cannot log in")
                .description("401 after reset")
                .status(TicketStatus.OPEN)
                .customer(customer)
                .createdAt(Instant.parse("2026-07-01T10:00:00Z"))
                .updatedAt(Instant.parse("2026-07-01T10:00:00Z"))
                .build();
        when(ticketRepository.save(any(Ticket.class))).thenReturn(saved);
        when(credentialsRepository.findAllById(List.of(2L))).thenReturn(List.of(customerCredentials));

        TicketResponse response = ticketService.createTicket(
                new CreateTicketRequest("Cannot log in", "401 after reset"));

        assertThat(response.id()).isEqualTo(saved.getExternalId());
        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(response.customer().username()).isEqualTo("cust1");
    }

    @Test
    void createTicketFailsWith500WhenCustomerRowMissing() {
        when(currentUserService.currentUser()).thenReturn(customerUser);
        when(customerRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.createTicket(new CreateTicketRequest("t", "d")))
                .isInstanceOf(ApiException.class)
                .extracting(t -> ((ApiException) t).getCode())
                .isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void listTicketsForAdminAppliesNoScope() {
        when(currentUserService.hasRole(Role.ADMIN)).thenReturn(true);
        when(ticketRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        List<TicketResponse> result = ticketService.listTickets(null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void listTicketsForAgentScopesByAgentId() {
        when(currentUserService.hasRole(Role.ADMIN)).thenReturn(false);
        when(currentUserService.hasRole(Role.AGENT)).thenReturn(true);
        when(currentUserService.currentUser()).thenReturn(agent);
        when(ticketRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        List<TicketResponse> result = ticketService.listTickets(null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void listTicketsForCustomerScopesByCustomerUserId() {
        when(currentUserService.hasRole(Role.ADMIN)).thenReturn(false);
        when(currentUserService.hasRole(Role.AGENT)).thenReturn(false);
        when(currentUserService.hasRole(Role.CUSTOMER)).thenReturn(true);
        when(currentUserService.currentUser()).thenReturn(customerUser);
        when(ticketRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        List<TicketResponse> result = ticketService.listTickets(TicketStatus.OPEN, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void getTicketReturnsWhenCallerIsOwningCustomer() {
        Ticket ticket = ticketOf(customer);
        when(ticketRepository.findByExternalId(ticket.getExternalId())).thenReturn(Optional.of(ticket));
        when(currentUserService.hasRole(Role.ADMIN)).thenReturn(false);
        when(currentUserService.hasRole(Role.AGENT)).thenReturn(false);
        when(currentUserService.hasRole(Role.CUSTOMER)).thenReturn(true);
        when(currentUserService.currentUser()).thenReturn(customerUser);
        when(credentialsRepository.findAllById(List.of(2L))).thenReturn(List.of(customerCredentials));

        TicketResponse response = ticketService.getTicket(ticket.getExternalId());

        assertThat(response.id()).isEqualTo(ticket.getExternalId());
    }

    @Test
    void getTicketReturns404WhenNotFound() {
        UUID nonexistent = UUID.randomUUID();
        when(ticketRepository.findByExternalId(nonexistent)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.getTicket(nonexistent))
                .isInstanceOf(ApiException.class)
                .extracting(t -> ((ApiException) t).getCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void getTicketReturns404WhenCustomerViewingAnothersTicket() {
        Ticket ticket = ticketOf(customer);
        User otherCustomer = User.builder().id(99L).email("other@ex.com").fullName("Other").build();
        when(ticketRepository.findByExternalId(ticket.getExternalId())).thenReturn(Optional.of(ticket));
        when(currentUserService.hasRole(Role.ADMIN)).thenReturn(false);
        when(currentUserService.hasRole(Role.AGENT)).thenReturn(false);
        when(currentUserService.hasRole(Role.CUSTOMER)).thenReturn(true);
        when(currentUserService.currentUser()).thenReturn(otherCustomer);

        assertThatThrownBy(() -> ticketService.getTicket(ticket.getExternalId()))
                .isInstanceOf(ApiException.class)
                .extracting(t -> ((ApiException) t).getCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void getTicketReturns404WhenAgentDoesNotOwnCustomer() {
        Ticket ticket = ticketOf(customer);
        User otherAgent = User.builder().id(42L).email("other-agent@ex.com").fullName("Other Agent").build();
        when(ticketRepository.findByExternalId(ticket.getExternalId())).thenReturn(Optional.of(ticket));
        when(currentUserService.hasRole(Role.ADMIN)).thenReturn(false);
        when(currentUserService.hasRole(Role.AGENT)).thenReturn(true);
        when(currentUserService.currentUser()).thenReturn(otherAgent);

        assertThatThrownBy(() -> ticketService.getTicket(ticket.getExternalId()))
                .isInstanceOf(ApiException.class)
                .extracting(t -> ((ApiException) t).getCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void getTicketAlwaysReturnsForAdmin() {
        Ticket ticket = ticketOf(customer);
        when(ticketRepository.findByExternalId(ticket.getExternalId())).thenReturn(Optional.of(ticket));
        when(currentUserService.hasRole(Role.ADMIN)).thenReturn(true);
        when(credentialsRepository.findAllById(List.of(2L))).thenReturn(List.of(customerCredentials));

        TicketResponse response = ticketService.getTicket(ticket.getExternalId());

        assertThat(response.id()).isEqualTo(ticket.getExternalId());
    }

    private Ticket ticketOf(Customer c) {
        return Ticket.builder()
                .id(500L)
                .externalId(UUID.randomUUID())
                .title("Any")
                .description("Any")
                .status(TicketStatus.OPEN)
                .customer(c)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
