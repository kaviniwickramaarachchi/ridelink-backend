package com.ridelink.account.service;

import com.ridelink.account.dto.request.*;
import com.ridelink.account.dto.response.*;
import com.ridelink.account.entity.*;
import com.ridelink.account.exception.ApiException;
import com.ridelink.account.repository.UserRepository;
import com.ridelink.account.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional
public class AccountService {
 private final UserRepository users;
 private final PasswordEncoder passwords;
 private final JwtService tokens;
 public AccountService(UserRepository users,PasswordEncoder passwords,JwtService tokens) {
  this.users=users; this.passwords=passwords; this.tokens=tokens;
 }
 private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
 public UserResponse register(RegisterRequest request) {
  if (request.role()==Role.ADMIN) throw new ApiException(HttpStatus.FORBIDDEN,"ADMIN cannot self-register");
  if (request.password().getBytes(StandardCharsets.UTF_8).length>72)
   throw new ApiException(HttpStatus.BAD_REQUEST,"Password must not exceed 72 UTF-8 bytes");
  String email=normalize(request.email());
  if (users.existsByEmail(email)) throw new ApiException(HttpStatus.CONFLICT,"An account with this email already exists");
  User user=new User(request.name().trim(),email,passwords.encode(request.password()),request.phoneNumber().trim(),request.role());
  return UserResponse.from(users.saveAndFlush(user));
 }
 @Transactional(readOnly=true)
 public LoginResponse login(LoginRequest request) {
  if (request.password().getBytes(StandardCharsets.UTF_8).length>72)
   throw new ApiException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
  User user=users.findByEmail(normalize(request.email())).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Invalid email or password"));
  if (!passwords.matches(request.password(),user.getPasswordHash())) throw new ApiException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
  if (user.getAccountStatus()!=AccountStatus.ACTIVE) throw new ApiException(HttpStatus.FORBIDDEN,"Account is inactive");
  return new LoginResponse(tokens.issue(user),"Bearer",JwtService.EXPIRY_SECONDS,UserResponse.from(user));
 }
 private User find(Long id) { return users.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"User not found")); }
 @Transactional(readOnly=true)
 public UserResponse profile(Long id) { return UserResponse.from(find(id)); }
 public UserResponse updateProfile(Long id,UpdateProfileRequest request) {
  if (request.name()==null && request.phoneNumber()==null) throw new ApiException(HttpStatus.BAD_REQUEST,"Supply name or phoneNumber");
  User user=find(id);
  if (request.name()!=null) user.setName(request.name().trim());
  if (request.phoneNumber()!=null) user.setPhoneNumber(request.phoneNumber().trim());
  return UserResponse.from(users.saveAndFlush(user));
 }
 @PreAuthorize("hasRole('ADMIN')")
 public UserResponse updateStatus(Long id,UpdateStatusRequest request) {
  User user=find(id); user.setAccountStatus(request.accountStatus()); return UserResponse.from(users.saveAndFlush(user));
 }
 @PreAuthorize("hasRole('ADMIN')")
 public UserResponse updateRole(Long id,UpdateRoleRequest request) {
  User user=find(id); user.setRole(request.role()); return UserResponse.from(users.saveAndFlush(user));
 }
}
