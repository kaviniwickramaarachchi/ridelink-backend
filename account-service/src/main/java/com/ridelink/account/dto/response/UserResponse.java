package com.ridelink.account.dto.response;

import com.ridelink.account.entity.*;
import java.time.Instant;
public record UserResponse(Long id, String name, String email, String phoneNumber, Role role,
 AccountStatus accountStatus, Instant createdAt, Instant updatedAt) {
 public static UserResponse from(User u) {
  return new UserResponse(u.getId(),u.getName(),u.getEmail(),u.getPhoneNumber(),u.getRole(),u.getAccountStatus(),u.getCreatedAt(),u.getUpdatedAt());
 }
}
