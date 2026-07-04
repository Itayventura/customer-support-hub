package com.surense.customerhub.agent;

import com.surense.customerhub.agent.dto.AgentResponse;
import com.surense.customerhub.agent.dto.CreateAgentRequest;
import com.surense.customerhub.auth.Credentials;
import com.surense.customerhub.auth.CredentialsRepository;
import com.surense.customerhub.auth.UserRole;
import com.surense.customerhub.auth.UserRoleRepository;
import com.surense.customerhub.common.Role;
import com.surense.customerhub.common.exception.ApiException;
import com.surense.customerhub.common.exception.ErrorCode;
import com.surense.customerhub.user.User;
import com.surense.customerhub.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AgentService {

    private final UserRepository userRepository;
    private final CredentialsRepository credentialsRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    public AgentService(
            UserRepository userRepository,
            CredentialsRepository credentialsRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            PlatformTransactionManager transactionManager
    ) {
        this.userRepository = userRepository;
        this.credentialsRepository = credentialsRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public AgentResponse createAgent(CreateAgentRequest request) {
        // BCrypt encode (~100ms) runs OUTSIDE the transaction so we don't hold a DB
        // connection during CPU-bound hashing.
        String passwordHash = passwordEncoder.encode(request.password());

        return transactionTemplate.execute(status -> {
            if (credentialsRepository.existsByUsername(request.username())) {
                throw new ApiException(ErrorCode.CONFLICT_DUPLICATE_USERNAME);
            }
            if (userRepository.existsByEmail(request.email())) {
                throw new ApiException(ErrorCode.CONFLICT_DUPLICATE_EMAIL);
            }

            User user = userRepository.save(User.builder()
                    .email(request.email())
                    .fullName(request.fullName())
                    .build());

            credentialsRepository.save(Credentials.builder()
                    .user(user)
                    .username(request.username())
                    .passwordHash(passwordHash)
                    .build());

            userRoleRepository.save(UserRole.builder()
                    .user(user)
                    .role(Role.AGENT)
                    .build());

            return new AgentResponse(request.username(), user.getEmail(), user.getFullName());
        });
    }

    @Transactional(readOnly = true)
    public List<AgentResponse> listAgents() {
        List<UserRole> agentRoles = userRoleRepository.findAllByRole(Role.AGENT);
        if (agentRoles.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = agentRoles.stream().map(ur -> ur.getUser().getId()).toList();
        Map<Long, Credentials> credsByUserId = credentialsRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(Credentials::getUserId, Function.identity()));

        return agentRoles.stream()
                .map(ur -> {
                    User user = ur.getUser();
                    Credentials creds = credsByUserId.get(user.getId());
                    String username = (creds != null) ? creds.getUsername() : "";
                    return new AgentResponse(username, user.getEmail(), user.getFullName());
                })
                .toList();
    }
}
