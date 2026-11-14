package com.ridelink.account;
import com.ridelink.account.dto.request.*;
import com.ridelink.account.entity.*;
import com.ridelink.account.exception.ApiException;
import com.ridelink.account.repository.UserRepository;
import com.ridelink.account.security.JwtService;
import com.ridelink.account.service.AccountService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
 @Mock UserRepository users; @Mock PasswordEncoder passwords; @Mock JwtService tokens;
 @InjectMocks AccountService service;
 @Test void duplicateEmailNeverHashesOrSaves() {
  when(users.existsByEmail("rider@example.com")).thenReturn(true);
  assertThrows(ApiException.class,()->service.register(new RegisterRequest("Rider","RIDER@example.com","TestPass123!","0771234567",Role.PASSENGER)));
  verifyNoInteractions(passwords,tokens);verify(users,never()).saveAndFlush(any());
 }
 @Test void inactiveAccountNeverReceivesToken() {
  User user=new User("Rider","rider@example.com","hash","0771234567",Role.PASSENGER);user.setAccountStatus(AccountStatus.INACTIVE);
  when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));when(passwords.matches("TestPass123!","hash")).thenReturn(true);
  assertThrows(ApiException.class,()->service.login(new LoginRequest(user.getEmail(),"TestPass123!")));verifyNoInteractions(tokens);
 }
 @Test void adminSelfRegistrationNeverWrites() {
  assertThrows(ApiException.class,()->service.register(new RegisterRequest("Admin","admin@example.com","TestPass123!","0771234567",Role.ADMIN)));
  verifyNoInteractions(users,passwords,tokens);
 }
 @Test void multibytePasswordCannotExceedBcryptLimit() {
  assertThrows(ApiException.class,()->service.register(new RegisterRequest("Rider","rider@example.com","\u00e9".repeat(40),"0771234567",Role.PASSENGER)));
  verifyNoInteractions(users,passwords,tokens);
 }
}
