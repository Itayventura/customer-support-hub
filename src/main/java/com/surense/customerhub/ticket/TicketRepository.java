package com.surense.customerhub.ticket;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    @EntityGraph(attributePaths = {"customer", "customer.user", "customer.agent"})
    Optional<Ticket> findByExternalId(UUID externalId);

    @Override
    @EntityGraph(attributePaths = {"customer", "customer.user", "customer.agent"})
    List<Ticket> findAll(Specification<Ticket> spec, Sort sort);
}
