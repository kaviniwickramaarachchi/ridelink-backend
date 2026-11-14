package com.ridelink.account.config;

import com.ridelink.account.entity.AccountStatus;
import com.ridelink.account.repository.UserRepository;
import com.ridelink.account.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
 @Bean SecretKeySpec jwtKey(@Value("${app.jwt.secret}") String secret) {
  byte[] bytes=secret.getBytes(StandardCharsets.UTF_8);
  if (bytes.length<32) throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes");
  return new SecretKeySpec(bytes,"HmacSHA256");
 }
 @Bean JwtEncoder jwtEncoder(SecretKeySpec key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
 @Bean JwtDecoder jwtDecoder(SecretKeySpec key) {
  NimbusJwtDecoder decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
  var claimConverter=MappedJwtClaimSetConverter.withDefaults(Map.of());
  decoder.setClaimSetConverter(claims->{
   var converted=claimConverter.convert(claims);
   // The default converter synthesizes iat if absent; the shared contract requires it in the token.
   if (!claims.containsKey("iat")) converted.remove("iat");
   return converted;
  });
  decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(java.time.Duration.ZERO), jwt -> {
   try {
    if (jwt.getExpiresAt()==null || jwt.getIssuedAt()==null || jwt.getClaimAsString("email")==null
      || jwt.getClaimAsString("email").isBlank() || jwt.getClaimAsString("role")==null
      || Long.parseLong(jwt.getClaimAsString("userId"))<=0) throw new IllegalArgumentException();
    com.ridelink.account.entity.Role.valueOf(jwt.getClaimAsString("role"));
    return OAuth2TokenValidatorResult.success();
   } catch (RuntimeException e) { return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Required JWT claims are invalid",null)); }
  }));
  return decoder;
 }
 @Bean SecurityFilterChain security(HttpSecurity http,UserRepository users,ObjectMapper mapper) throws Exception {
  org.springframework.security.web.AuthenticationEntryPoint unauthorized=(request,response,error)->{
   response.setStatus(401); response.setContentType("application/json"); response.setHeader("WWW-Authenticate","Bearer");
   mapper.writeValue(response.getOutputStream(),ApiError.of(HttpStatus.UNAUTHORIZED,"Missing or invalid authentication",request.getRequestURI(),Map.of()));
  };
  http.csrf(csrf->csrf.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .requestCache(cache->cache.disable())
   .authorizeHttpRequests(auth->auth
    .requestMatchers("/api/v1/auth/register","/api/v1/auth/login","/swagger-ui/**","/swagger-ui.html","/v3/api-docs/**").permitAll()
    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN").anyRequest().authenticated())
   .exceptionHandling(errors->errors.authenticationEntryPoint(unauthorized).accessDeniedHandler((request,response,error)->{
    response.setStatus(403); response.setContentType("application/json");
    mapper.writeValue(response.getOutputStream(),ApiError.of(HttpStatus.FORBIDDEN,"Access denied",request.getRequestURI(),Map.of()));
   }))
   .oauth2ResourceServer(oauth->oauth.authenticationEntryPoint(unauthorized).jwt(jwt->jwt.jwtAuthenticationConverter(token->{
    // Reload this service's own account so deactivation and role changes apply immediately here.
    Long id=Long.valueOf(token.getClaimAsString("userId"));
    var user=users.findById(id).filter(u->u.getAccountStatus()==AccountStatus.ACTIVE)
     .orElseThrow(()->new OAuth2AuthenticationException("invalid_token"));
    return new UsernamePasswordAuthenticationToken(id,null,List.of(new SimpleGrantedAuthority("ROLE_"+user.getRole().name())));
   })));
  return http.build();
 }
}
