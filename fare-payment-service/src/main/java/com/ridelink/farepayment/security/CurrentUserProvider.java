package com.ridelink.farepayment.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    public Long getCurrentUserId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Long userId) {
            return userId;
        }

        if (principal instanceof Number number) {
            return number.longValue();
        }

        if (principal instanceof String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        return null;
    }

    public boolean hasRole(String role) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String expectedRole = "ROLE_" + role;

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (expectedRole.equals(authority.getAuthority())) {
                return true;
            }
        }

        return false;
    }
}