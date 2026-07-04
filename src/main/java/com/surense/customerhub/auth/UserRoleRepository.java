package com.surense.customerhub.auth;

import com.surense.customerhub.common.Role;
import com.surense.customerhub.user.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findAllByUser(User user);

    List<UserRole> findAllByUserId(Long userId);

    @EntityGraph(attributePaths = "user")
    List<UserRole> findAllByRole(Role role);

    boolean existsByUserAndRole(User user, Role role);
}
