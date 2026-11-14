package com.ridelink.ride.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedCurrentPassengerProvider implements CurrentPassengerProvider {

    @Override
    public Long getCurrentPassengerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long accountId)) {
            throw new IllegalStateException("Authenticated passenger identity is unavailable");
        }
        return accountId;
    }
}
