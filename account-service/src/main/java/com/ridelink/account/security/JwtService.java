package com.ridelink.account.security;

import com.ridelink.account.entity.User;
import java.time.Instant;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
@Service
public class JwtService {
 public static final long EXPIRY_SECONDS=3600;
 private final JwtEncoder encoder;
 public JwtService(JwtEncoder encoder) { this.encoder=encoder; }
 public String issue(User user) {
  Instant now=Instant.now();
  JwtClaimsSet claims=JwtClaimsSet.builder().subject(user.getId().toString()).issuedAt(now)
   .expiresAt(now.plusSeconds(EXPIRY_SECONDS)).claim("userId",user.getId())
   .claim("email",user.getEmail()).claim("role",user.getRole().name()).build();
  return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
 }
}
