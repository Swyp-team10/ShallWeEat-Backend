package org.example.shallweeatbackend.repository;

import org.example.shallweeatbackend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByProviderId(String providerId);
}
