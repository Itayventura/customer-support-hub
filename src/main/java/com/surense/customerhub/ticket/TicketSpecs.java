package com.surense.customerhub.ticket;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/**
 * Composable Specifications used by {@link TicketService} to build role-scoped,
 * filterable ticket queries without hand-writing JPQL.
 */
final class TicketSpecs {

    private TicketSpecs() {
    }

    /** Tickets belonging to a specific customer (by user_id, which is the customer PK). */
    static Specification<Ticket> ownedByCustomer(Long customerUserId) {
        return (root, query, cb) -> cb.equal(root.get("customer").get("userId"), customerUserId);
    }

    /** Tickets whose owning customer is registered under the given agent. */
    static Specification<Ticket> ownedByAgent(Long agentId) {
        return (root, query, cb) -> cb.equal(root.get("customer").get("agent").get("id"), agentId);
    }

    static Specification<Ticket> hasStatus(TicketStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<Ticket> createdAtOnOrAfter(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    static Specification<Ticket> createdAtOnOrBefore(Instant to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
