package com.surense.customerhub.customer;

import com.surense.customerhub.auth.Credentials;
import com.surense.customerhub.auth.CredentialsRepository;
import com.surense.customerhub.auth.CurrentUserService;
import com.surense.customerhub.auth.UserRole;
import com.surense.customerhub.auth.UserRoleRepository;
import com.surense.customerhub.common.Role;
import com.surense.customerhub.common.exception.ApiException;
import com.surense.customerhub.common.exception.ErrorCode;
import com.surense.customerhub.customer.dto.CreateCustomerRequest;
import com.surense.customerhub.customer.dto.CustomerResponse;
import com.surense.customerhub.user.User;
import com.surense.customerhub.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CurrentUserService currentUserService;
    @Mock private UserRepository userRepository;
    @Mock private CredentialsRepository credentialsRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private CustomerService customerService;

    private User agent;
    private Credentials agentCreds;

    @BeforeEach
    void setUp() {
        agent = User.builder().id(1L).email("agent@example.com").fullName("Agent One").build();
        agentCreds = Credentials.builder().userId(1L).user(agent).username("agent1").passwordHash("h").build();
    }

    @Test
    void createCustomerPersistsUserCredentialsRoleAndCustomerRow() {
        when(currentUserService.currentUser()).thenReturn(agent);
        when(credentialsRepository.existsByUsername("cust1")).thenReturn(false);
        when(userRepository.existsByEmail("cust1@example.com")).thenReturn(false);
        when(passwordEncoder.encode("s3cret-pw")).thenReturn("hashed");
        User customerUser = User.builder().id(50L).email("cust1@example.com").fullName("Customer One").build();
        when(userRepository.save(any(User.class))).thenReturn(customerUser);
        when(currentUserService.currentCredentials()).thenReturn(agentCreds);

        CustomerResponse response = customerService.createCustomer(new CreateCustomerRequest(
                "cust1", "s3cret-pw", "cust1@example.com", "Customer One"));

        assertThat(response.username()).isEqualTo("cust1");
        assertThat(response.agent().username()).isEqualTo("agent1");
        assertThat(response.agent().fullName()).isEqualTo("Agent One");

        verify(credentialsRepository).save(any(Credentials.class));
        verify(userRoleRepository).save(any(UserRole.class));
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void createCustomerRejectsDuplicateUsername() {
        when(currentUserService.currentUser()).thenReturn(agent);
        when(credentialsRepository.existsByUsername("taken")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(new CreateCustomerRequest(
                "taken", "s3cret-pw", "new@example.com", "Any Name")))
                .isInstanceOf(ApiException.class)
                .extracting(t -> ((ApiException) t).getCode())
                .isEqualTo(ErrorCode.CONFLICT_DUPLICATE_USERNAME);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createCustomerRejectsDuplicateEmail() {
        when(currentUserService.currentUser()).thenReturn(agent);
        when(credentialsRepository.existsByUsername("newcust")).thenReturn(false);
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(new CreateCustomerRequest(
                "newcust", "s3cret-pw", "taken@example.com", "Any Name")))
                .isInstanceOf(ApiException.class)
                .extracting(t -> ((ApiException) t).getCode())
                .isEqualTo(ErrorCode.CONFLICT_DUPLICATE_EMAIL);

        verify(userRepository, never()).save(any());
    }

    @Test
    void listCustomersForAdminReturnsAll() {
        when(currentUserService.hasRole(Role.ADMIN)).thenReturn(true);
        when(customerRepository.findAll()).thenReturn(List.of());

        assertThat(customerService.listCustomers()).isEmpty();
        verify(customerRepository).findAll();
    }

    @Test
    void listCustomersForAgentReturnsOnlyOwn() {
        when(currentUserService.hasRole(Role.ADMIN)).thenReturn(false);
        when(currentUserService.currentUser()).thenReturn(agent);
        when(customerRepository.findAllByAgentId(1L)).thenReturn(List.of());

        assertThat(customerService.listCustomers()).isEmpty();
        verify(customerRepository).findAllByAgentId(1L);
    }
}
