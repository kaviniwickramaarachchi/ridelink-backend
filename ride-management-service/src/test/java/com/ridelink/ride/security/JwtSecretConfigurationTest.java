package com.ridelink.ride.security;

import com.ridelink.ride.config.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtSecretConfigurationTest {

    private final SecurityConfiguration configuration = new SecurityConfiguration();

    @Test
    void missingSecretFailsClearly() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> configuration.jwtSecretKey(new MockEnvironment())
        );

        assertEquals("JWT_SECRET is required", exception.getMessage());
    }

    @Test
    void blankSecretFailsClearly() {
        MockEnvironment environment = new MockEnvironment().withProperty("JWT_SECRET", " ");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> configuration.jwtSecretKey(environment)
        );

        assertEquals("JWT_SECRET must not be blank", exception.getMessage());
    }

    @Test
    void shortUtf8SecretFailsClearly() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("JWT_SECRET", "1234567890123456789012345678901");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> configuration.jwtSecretKey(environment)
        );

        assertTrue(exception.getMessage().contains("at least 32 UTF-8 bytes"));
    }
}
