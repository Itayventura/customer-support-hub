package com.surense.customerhub.ticket;

import com.surense.customerhub.customer.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    List<Ticket> findAllByCustomer(Customer customer);

    List<Ticket> findAllByCustomer_UserId(Long customerUserId);

    Optional<Ticket> findByExternalId(UUID externalId);
}
