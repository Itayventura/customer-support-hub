package com.surense.customerhub.ticket;

import com.surense.customerhub.customer.Customer;
import com.surense.customerhub.customer.CustomerRepository;
import com.surense.customerhub.user.User;
import com.surense.customerhub.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TicketRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Test
    void ticketBelongsToCustomerAndListsByCustomer() {
        User agent = userRepository.save(User.builder()
                .email("agent@example.com")
                .fullName("Agent")
                .build());

        User customerUser = userRepository.save(User.builder()
                .email("customer@example.com")
                .fullName("Customer")
                .build());

        Customer customer = customerRepository.save(Customer.builder()
                .user(customerUser)
                .agent(agent)
                .build());

        assertThat(customer.getUserId()).isEqualTo(customerUser.getId());

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .title("Cannot log in")
                .description("Getting 401 after password reset.")
                .status(TicketStatus.OPEN)
                .customer(customer)
                .build());

        assertThat(ticket.getId()).isNotNull();
        assertThat(ticket.getExternalId()).isNotNull();
        assertThat(ticket.getCreatedAt()).isNotNull();

        List<Ticket> tickets = ticketRepository.findAllByCustomer_UserId(customer.getUserId());
        assertThat(tickets).hasSize(1);
        assertThat(tickets.get(0).getTitle()).isEqualTo("Cannot log in");
        assertThat(tickets.get(0).getStatus()).isEqualTo(TicketStatus.OPEN);

        assertThat(ticketRepository.findByExternalId(ticket.getExternalId()))
                .isPresent()
                .get()
                .extracting(Ticket::getId)
                .isEqualTo(ticket.getId());
    }
}
