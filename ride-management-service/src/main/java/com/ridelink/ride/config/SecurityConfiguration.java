package com.ridelink.ride.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.ridelink.ride.security.RideJwtClaimsValidator;
import com.ridelink.ride.security.SecurityErrorResponseWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    @Bean
    public SecretKeySpec jwtSecretKey(Environment environment) {
        String secret = environment.getProperty("JWT_SECRET");
        if (secret == null) {
            throw new IllegalStateException("JWT_SECRET is required");
        }
        if (secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must not be blank");
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKeySpec jwtSecretKey, RideJwtClaimsValidator claimsValidator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        MappedJwtClaimSetConverter claimConverter = MappedJwtClaimSetConverter.withDefaults(Map.of());
        decoder.setClaimSetConverter(claims -> {
            Map<String, Object> converted = claimConverter.convert(claims);
            if (!claims.containsKey("iat")) {
                converted.remove("iat");
            }
            return converted;
        });
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO),
                claimsValidator
        ));
        return decoder;
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken>
            jwtAuthenticationConverter() {
        return token -> {
            Long accountId = token.getClaim("userId");
            String role = token.getClaimAsString("role");
            return new UsernamePasswordAuthenticationToken(
                    accountId,
                    token,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityErrorResponseWriter securityErrors,
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrors::writeUnauthorized)
                        .accessDeniedHandler(securityErrors::writeForbidden))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/error"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/rides/driver",
                                "/api/v1/rides/driver/{rideId}"
                        ).hasRole("DRIVER")
                        .requestMatchers(HttpMethod.PATCH,
                                "/api/v1/rides/{rideId}/accept",
                                "/api/v1/rides/{rideId}/start",
                                "/api/v1/rides/{rideId}/complete"
                        ).hasRole("DRIVER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/rides").hasRole("PASSENGER")
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/rides",
                                "/api/v1/rides/{rideId}"
                        ).hasRole("PASSENGER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/rides/{rideId}/cancel")
                        .hasRole("PASSENGER")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(securityErrors::writeUnauthorized)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }
}
