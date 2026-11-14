package com.ridelink.ride.security;

import com.ridelink.ride.client.DriverServiceClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedCurrentDriverProvider implements CurrentDriverProvider {

    private final DriverServiceClient driverServiceClient;

    public AuthenticatedCurrentDriverProvider(DriverServiceClient driverServiceClient) {
        this.driverServiceClient = driverServiceClient;
    }

    @Override
    public Long getCurrentDriverId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long accountId)) {
            throw new IllegalStateException("Authenticated driver identity is unavailable");
        }
        return driverServiceClient.resolveDriverId(accountId);
    }
}
