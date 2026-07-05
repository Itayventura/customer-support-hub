package com.surense.customerhub.agent;

import com.surense.customerhub.agent.dto.AgentResponse;
import com.surense.customerhub.agent.dto.CreateAgentRequest;
import com.surense.customerhub.auth.Credentials;
import com.surense.customerhub.auth.CredentialsRepository;
import com.surense.customerhub.auth.CurrentUserService;
import com.surense.customerhub.auth.UserRole;
import com.surense.customerhub.auth.UserRoleRepository;
import com.surense.customerhub.common.Role;
import com.surense.customerhub.common.exception.ApiException;
import com.surense.customerhub.common.exception.ErrorCode;
import com.surense.customerhub.user.User;
import com.surense.customerhub.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock private CurrentUserService currentUserService;
    @Mock private UserRepository userRepository;
    @Mock private CredentialsRepository credentialsRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks private AgentService agentService;

    @Test
    void createAgentPersistsUserCredentialsAndRole() {
        when(credentialsRepository.existsByUsername("newagent")).thenReturn(false);
        when(userRepository.existsByEmail("newagent@example.com")).thenReturn(false);
        when(passwordEncoder.encode("s3cure-pass")).thenReturn("hashed");
        User savedUser = User.builder().id(10L).email("newagent@example.com").fullName("New Agent").build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        AgentResponse response = agentService.createAgent(new CreateAgentRequest(
                "newagent", "s3cure-pass", "newagent@example.com", "New Agent"));

        assertThat(response.username()).isEqualTo("newagent");
        assertThat(response.email()).isEqualTo("newagent@example.com");
        assertThat(response.fullName()).isEqualTo("New Agent");
        verify(credentialsRepository).save(any(Credentials.class));
        verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    void createAgentRejectsDuplicateUsernameWith409() {
        when(credentialsRepository.existsByUsername("taken")).thenReturn(true);

        assertThatThrownBy(() -> agentService.createAgent(new CreateAgentRequest(
                "taken", "s3cure-pass", "any@example.com", "Any Name")))
                .isInstanceOf(ApiException.class)
                .extracting(t -> ((ApiException) t).getCode())
                .isEqualTo(ErrorCode.CONFLICT_DUPLICATE_USERNAME);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createAgentRejectsDuplicateEmailWith409() {
        when(credentialsRepository.existsByUsername("newagent")).thenReturn(false);
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> agentService.createAgent(new CreateAgentRequest(
                "newagent", "s3cure-pass", "taken@example.com", "Any Name")))
                .isInstanceOf(ApiException.class)
                .extracting(t -> ((ApiException) t).getCode())
                .isEqualTo(ErrorCode.CONFLICT_DUPLICATE_EMAIL);

        verify(userRepository, never()).save(any());
    }

    @Test
    void listAgentsReturnsAllUsersWithAgentRole() {
        User a1 = User.builder().id(1L).email("a1@example.com").fullName("Agent One").build();
        User a2 = User.builder().id(2L).email("a2@example.com").fullName("Agent Two").build();
        Credentials c1 = Credentials.builder().userId(1L).user(a1).username("a1").passwordHash("h").build();
        Credentials c2 = Credentials.builder().userId(2L).user(a2).username("a2").passwordHash("h").build();

        when(userRoleRepository.findAllByRole(Role.AGENT)).thenReturn(List.of(
                UserRole.builder().user(a1).role(Role.AGENT).build(),
                UserRole.builder().user(a2).role(Role.AGENT).build()));
        when(credentialsRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(c1, c2));

        List<AgentResponse> agents = agentService.listAgents();

        assertThat(agents).hasSize(2);
        assertThat(agents).extracting(AgentResponse::username).containsExactlyInAnyOrder("a1", "a2");
    }

    @Test
    void listAgentsReturnsEmptyWhenNoneExist() {
        when(userRoleRepository.findAllByRole(Role.AGENT)).thenReturn(List.of());
        assertThat(agentService.listAgents()).isEmpty();
    }
}
