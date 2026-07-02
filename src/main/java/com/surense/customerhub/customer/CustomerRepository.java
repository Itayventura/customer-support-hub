package com.surense.customerhub.customer;

import com.surense.customerhub.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findAllByAgent(User agent);

    List<Customer> findAllByAgentId(Long agentId);
}
