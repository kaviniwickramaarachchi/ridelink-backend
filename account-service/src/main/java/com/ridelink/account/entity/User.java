package com.ridelink.account.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
public class User {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
 @Column(nullable=false, length=100) private String name;
 @Column(nullable=false, length=254) private String email;
 @Column(name="password_hash", nullable=false, length=100) private String passwordHash;
 @Column(name="phone_number", nullable=false, length=20) private String phoneNumber;
 @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable=false, length=20) private Role role;
 @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name="account_status", nullable=false, length=20) private AccountStatus accountStatus;
 @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
 @Column(name="updated_at", nullable=false) private Instant updatedAt;
 protected User() {}
 public User(String name, String email, String passwordHash, String phoneNumber, Role role) {
  this.name=name; this.email=email; this.passwordHash=passwordHash; this.phoneNumber=phoneNumber;
  this.role=role; this.accountStatus=AccountStatus.ACTIVE;
 }
 @PrePersist void created() { createdAt=Instant.now(); updatedAt=createdAt; }
 @PreUpdate void updated() { updatedAt=Instant.now(); }
 public Long getId() { return id; }
 public String getName() { return name; }
 public String getEmail() { return email; }
 public String getPasswordHash() { return passwordHash; }
 public String getPhoneNumber() { return phoneNumber; }
 public Role getRole() { return role; }
 public AccountStatus getAccountStatus() { return accountStatus; }
 public Instant getCreatedAt() { return createdAt; }
 public Instant getUpdatedAt() { return updatedAt; }
 public void setName(String name) { this.name=name; }
 public void setPhoneNumber(String phoneNumber) { this.phoneNumber=phoneNumber; }
 public void setRole(Role role) { this.role=role; }
 public void setAccountStatus(AccountStatus status) { this.accountStatus=status; }
}
