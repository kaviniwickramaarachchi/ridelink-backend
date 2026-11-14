package com.ridelink.farepayment.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CurrentUserProviderTest {

    private final CurrentUserProvider provider = new CurrentUserProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserId_withAuthenticatedNumericSubject_returnsId() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", null,
                        List.of(new SimpleGrantedAuthority("ROLE_PASSENGER"))));

        assertEquals(42L, provider.getCurrentUserId());
    }

    @Test
    void getCurrentUserId_withNoAuthentication_returnsNull() {
        assertNull(provider.getCurrentUserId());
    }

    @Test
    void hasRole_withMatchingAuthority_returnsTrue() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("1", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertTrue(provider.hasRole("ADMIN"));
    }

    @Test
    void hasRole_withoutMatchingAuthority_returnsFalse() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("1", null,
                        List.of(new SimpleGrantedAuthority("ROLE_PASSENGER"))));

        assertFalse(provider.hasRole("ADMIN"));
    }
}
