package com.ridelink.ride.security;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class RideJwtClaimsValidator implements OAuth2TokenValidator<Jwt> {

    private static final Set<String> ROLES = Set.of("PASSENGER", "DRIVER", "ADMIN");
    private static final OAuth2Error INVALID_CLAIMS =
            new OAuth2Error("invalid_token", "Required JWT claims are invalid", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            Object rawUserId = jwt.getClaims().get("userId");
            if (!(rawUserId instanceof Number number)) {
                return failure();
            }
            long userId = new BigDecimal(number.toString()).longValueExact();
            if (userId <= 0 || !Long.toString(userId).equals(jwt.getSubject())) {
                return failure();
            }

            Object rawRole = jwt.getClaims().get("role");
            Object rawEmail = jwt.getClaims().get("email");
            if (!(rawRole instanceof String role) || !ROLES.contains(role)
                    || !(rawEmail instanceof String email) || email.isBlank()) {
                return failure();
            }

            Instant issuedAt = jwt.getIssuedAt();
            Instant expiresAt = jwt.getExpiresAt();
            Instant now = Instant.now();
            if (issuedAt == null || expiresAt == null || issuedAt.isAfter(now)
                    || !issuedAt.isBefore(expiresAt) || !expiresAt.isAfter(now)) {
                return failure();
            }
            return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException exception) {
            return failure();
        }
    }

    private OAuth2TokenValidatorResult failure() {
        return OAuth2TokenValidatorResult.failure(INVALID_CLAIMS);
    }
}
