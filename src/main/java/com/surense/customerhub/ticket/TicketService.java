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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final CurrentUserService currentUserService;
    private final CustomerRepository customerRepository;
    private final TicketRepository ticketRepository;
    private final CredentialsRepository credentialsRepository;

    public TicketService(
            CurrentUserService currentUserService,
            CustomerRepository customerRepository,
            TicketRepository ticketRepository,
            CredentialsRepository credentialsRepository
    ) {
        this.currentUserService = currentUserService;
        this.customerRepository = customerRepository;
        this.ticketRepository = ticketRepository;
        this.credentialsRepository = credentialsRepository;
    }

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        User current = currentUserService.currentUser();
        Customer customer = customerRepository.findById(current.getId())
                .orElseThrow(() -> {
                    log.error("Data integrity: CUSTOMER user id={} has no matching customers row", current.getId());
                    return new ApiException(ErrorCode.INTERNAL_ERROR);
                });

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .title(request.title())
                .description(request.description())
                .status(TicketStatus.OPEN)
                .customer(customer)
                .build());

        log.info("Ticket created actor={} externalId={}", currentUserService.currentUsername(), ticket.getExternalId());
        return toResponses(List.of(ticket)).get(0);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> listTickets(TicketStatus status, Instant from, Instant to) {
        List<Specification<Ticket>> parts = new ArrayList<>();

        // Role-scoped visibility. ADMIN sees everything; AGENT sees own customers'; CUSTOMER sees own.
        if (currentUserService.hasRole(Role.ADMIN)) {
            // no scoping
        } else if (currentUserService.hasRole(Role.AGENT)) {
            parts.add(TicketSpecs.ownedByAgent(currentUserService.currentUser().getId()));
        } else if (currentUserService.hasRole(Role.CUSTOMER)) {
            parts.add(TicketSpecs.ownedByCustomer(currentUserService.currentUser().getId()));
        } else {
            // Authenticated but no known role → see nothing.
            return List.of();
        }

        if (status != null) parts.add(TicketSpecs.hasStatus(status));
        if (from != null) parts.add(TicketSpecs.createdAtOnOrAfter(from));
        if (to != null) parts.add(TicketSpecs.createdAtOnOrBefore(to));

        List<Ticket> tickets = ticketRepository.findAll(
                Specification.allOf(parts),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return toResponses(tickets);
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicket(UUID externalId) {
        Ticket ticket = ticketRepository.findByExternalId(externalId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!isVisibleToCaller(ticket)) {
            // Deliberately 404 (not 403) — don't leak the ticket's existence to non-owners.
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return toResponses(List.of(ticket)).get(0);
    }

    private boolean isVisibleToCaller(Ticket ticket) {
        if (currentUserService.hasRole(Role.ADMIN)) {
            return true;
        }
        User current = currentUserService.currentUser();
        Customer customer = ticket.getCustomer();
        if (currentUserService.hasRole(Role.AGENT)) {
            return customer.getAgent().getId().equals(current.getId());
        }
        if (currentUserService.hasRole(Role.CUSTOMER)) {
            return customer.getUserId().equals(current.getId());
        }
        return false;
    }

    private List<TicketResponse> toResponses(List<Ticket> tickets) {
        if (tickets.isEmpty()) {
            return List.of();
        }
        List<Long> customerUserIds = tickets.stream()
                .map(t -> t.getCustomer().getUserId())
                .distinct()
                .toList();
        Map<Long, Credentials> credsById = credentialsRepository.findAllById(customerUserIds).stream()
                .collect(Collectors.toMap(Credentials::getUserId, Function.identity()));

        return tickets.stream().map(t -> {
            Customer customer = t.getCustomer();
            User customerUser = customer.getUser();
            Credentials cred = credsById.get(customer.getUserId());
            return new TicketResponse(
                    t.getExternalId(),
                    t.getTitle(),
                    t.getDescription(),
                    t.getStatus(),
                    t.getCreatedAt(),
                    t.getUpdatedAt(),
                    new TicketResponse.CustomerRef(
                            cred != null ? cred.getUsername() : "",
                            customerUser.getFullName()
                    )
            );
        }).toList();
    }
}
