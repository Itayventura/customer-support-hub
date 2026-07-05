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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final CredentialsRepository credentialsRepository;
    private final UserRoleRepository userRoleRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    public CustomerService(
            CurrentUserService currentUserService,
            UserRepository userRepository,
            CredentialsRepository credentialsRepository,
            UserRoleRepository userRoleRepository,
            CustomerRepository customerRepository,
            PasswordEncoder passwordEncoder,
            PlatformTransactionManager transactionManager
    ) {
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.credentialsRepository = credentialsRepository;
        this.userRoleRepository = userRoleRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        User agent = currentUserService.currentUser();
        Credentials agentCredentials = currentUserService.currentCredentials();
        String passwordHash = passwordEncoder.encode(request.password());

        CustomerResponse response = transactionTemplate.execute(status -> {
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
                    .role(Role.CUSTOMER)
                    .build());

            customerRepository.save(Customer.builder()
                    .user(user)
                    .agent(agent)
                    .build());

            return new CustomerResponse(
                    request.username(),
                    user.getEmail(),
                    user.getFullName(),
                    new CustomerResponse.AgentRef(agentCredentials.getUsername(), agent.getFullName())
            );
        });
        log.info("Customer created actor={} newCustomerUsername={}", agentCredentials.getUsername(), request.username());
        return response;
    }

    /**
     * ADMIN sees every customer; AGENT sees only their own.
     */
    @Transactional(readOnly = true)
    public List<CustomerResponse> listCustomers() {
        List<Customer> customers;
        if (currentUserService.hasRole(Role.ADMIN)) {
            customers = customerRepository.findAll();
        } else {
            User agent = currentUserService.currentUser();
            customers = customerRepository.findAllByAgentId(agent.getId());
        }
        return toResponses(customers);
    }

    private List<CustomerResponse> toResponses(List<Customer> customers) {
        if (customers.isEmpty()) {
            return List.of();
        }

        // Gather all user ids we need credentials for (customer users + their agents), one query each.
        List<Long> customerUserIds = customers.stream().map(Customer::getUserId).toList();
        List<Long> agentUserIds = customers.stream().map(c -> c.getAgent().getId()).distinct().toList();

        Map<Long, Credentials> customerCreds = credentialsRepository.findAllById(customerUserIds).stream()
                .collect(Collectors.toMap(Credentials::getUserId, Function.identity()));
        Map<Long, Credentials> agentCreds = credentialsRepository.findAllById(agentUserIds).stream()
                .collect(Collectors.toMap(Credentials::getUserId, Function.identity()));

        return customers.stream()
                .map(c -> {
                    User customerUser = c.getUser();
                    Credentials custCreds = customerCreds.get(c.getUserId());
                    User agentUser = c.getAgent();
                    Credentials agtCreds = agentCreds.get(agentUser.getId());
                    return new CustomerResponse(
                            custCreds != null ? custCreds.getUsername() : "",
                            customerUser.getEmail(),
                            customerUser.getFullName(),
                            new CustomerResponse.AgentRef(
                                    agtCreds != null ? agtCreds.getUsername() : "",
                                    agentUser.getFullName()
                            )
                    );
                })
                .toList();
    }
}
