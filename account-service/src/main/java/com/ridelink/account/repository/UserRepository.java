package com.ridelink.account.repository;

import com.ridelink.account.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserRepository extends JpaRepository<User, Long> {
 boolean existsByEmail(String email);
 Optional<User> findByEmail(String email);
}
