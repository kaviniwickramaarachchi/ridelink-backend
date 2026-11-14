package com.ridelink.driver.controller;

import com.ridelink.driver.entity.AvailabilityStatus;
import com.ridelink.driver.dto.response.DriverIdResponse;
import com.ridelink.driver.dto.response.DriverProfileResponse;
import com.ridelink.driver.dto.response.EligibleDriverResponse;
import com.ridelink.driver.security.InternalApiKeyFilter;
import com.ridelink.driver.security.SecurityConfig;
import com.ridelink.driver.security.SecurityErrorResponseWriter;
import com.ridelink.driver.service.DriverProfileService;
import com.ridelink.driver.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        DriverController.class,
        InternalDriverController.class
})
@Import({
        SecurityConfig.class,
        InternalApiKeyFilter.class,
        SecurityErrorResponseWriter.class
})
@TestPropertySource(properties = {
        "security.jwt.secret=test-jwt-secret-that-is-at-least-32-characters",
        "security.internal-api-key=test-internal-api-key"
})
class DriverControllerSecurityTest {

    private static final Long ACCOUNT_ID = 42L;
    private static final Long DRIVER_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DriverProfileService driverProfileService;

    @MockitoBean
    private VehicleService vehicleService;

    @Test
    void driverCanCreateProfileUsingAccountIdFromJwt()
            throws Exception {

        when(driverProfileService.createProfile(ACCOUNT_ID))
                .thenReturn(profileResponse(
                        AvailabilityStatus.UNAVAILABLE));

        mockMvc.perform(post("/api/v1/drivers/profile")
                        .with(driverJwt()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(DRIVER_ID))
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.availabilityStatus")
                        .value("UNAVAILABLE"));

        verify(driverProfileService).createProfile(ACCOUNT_ID);
    }

    @Test
    void protectedEndpointRejectsMissingJwt() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Authentication is required or the token is invalid"));
    }

    @Test
    void protectedEndpointRejectsNonDriverRole()
            throws Exception {

        mockMvc.perform(get("/api/v1/drivers/me")
                        .with(passengerJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message")
                        .value("Access is denied"));
    }

    @Test
    void invalidVehicleRequestReturnsValidationErrors()
            throws Exception {

        mockMvc.perform(post("/api/v1/drivers/me/vehicles")
                        .with(driverJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"))
                .andExpect(jsonPath(
                        "$.fieldErrors.registrationNumber")
                        .value("Registration number is required"));

        verify(vehicleService, never())
                .addVehicle(any(), any());
    }

    @Test
    void validInternalKeyCanFindEligibleDrivers()
            throws Exception {

        when(driverProfileService.findEligibleDrivers("Colombo"))
                .thenReturn(List.of(
                        new EligibleDriverResponse(
                                DRIVER_ID,
                                ACCOUNT_ID,
                                "Colombo",
                                null,
                                null)));

        mockMvc.perform(get("/internal/drivers/eligible")
                        .header(
                                InternalApiKeyFilter
                                        .INTERNAL_API_KEY_HEADER,
                                "test-internal-api-key")
                        .param("serviceArea", "Colombo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].driverId")
                        .value(DRIVER_ID))
                .andExpect(jsonPath("$[0].accountId")
                        .value(ACCOUNT_ID))
                .andExpect(jsonPath("$[0].serviceArea")
                        .value("Colombo"));

        verify(driverProfileService)
                .findEligibleDrivers("Colombo");
    }

    @Test
    void internalEndpointRejectsMissingApiKey()
            throws Exception {

        mockMvc.perform(get("/internal/drivers/eligible")
                        .param("serviceArea", "Colombo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Missing or invalid internal API key"));

        verify(driverProfileService, never())
                .findEligibleDrivers(any());
    }

    @Test
    void internalEndpointRejectsIncorrectApiKey()
            throws Exception {

        mockMvc.perform(get("/internal/drivers/eligible")
                        .header(
                                InternalApiKeyFilter
                                        .INTERNAL_API_KEY_HEADER,
                                "incorrect-key")
                        .param("serviceArea", "Colombo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verify(driverProfileService, never())
                .findEligibleDrivers(any());
    }

    @Test
    void unavailableInternalRequestReservesDriver()
            throws Exception {

        when(driverProfileService.reserveDriver(DRIVER_ID))
                .thenReturn(profileResponse(
                        AvailabilityStatus.UNAVAILABLE));

        mockMvc.perform(patch(
                        "/internal/drivers/{driverId}/availability",
                        DRIVER_ID)
                        .header(
                                InternalApiKeyFilter
                                        .INTERNAL_API_KEY_HEADER,
                                "test-internal-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "availabilityStatus": "UNAVAILABLE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availabilityStatus")
                        .value("UNAVAILABLE"));

        verify(driverProfileService).reserveDriver(DRIVER_ID);
        verify(driverProfileService, never())
                .releaseDriver(DRIVER_ID);
    }

    @Test
    void availableInternalRequestReleasesDriver()
            throws Exception {

        when(driverProfileService.releaseDriver(DRIVER_ID))
                .thenReturn(profileResponse(
                        AvailabilityStatus.AVAILABLE));

        mockMvc.perform(patch(
                        "/internal/drivers/{driverId}/availability",
                        DRIVER_ID)
                        .header(
                                InternalApiKeyFilter
                                        .INTERNAL_API_KEY_HEADER,
                                "test-internal-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "availabilityStatus": "AVAILABLE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availabilityStatus")
                        .value("AVAILABLE"));

        verify(driverProfileService).releaseDriver(DRIVER_ID);
        verify(driverProfileService, never())
                .reserveDriver(DRIVER_ID);
    }

    @Test
    void validInternalKeyCanResolveDriverId()
            throws Exception {

        when(driverProfileService
                .resolveDriverIdByAccountId(ACCOUNT_ID))
                .thenReturn(new DriverIdResponse(
                        ACCOUNT_ID,
                        DRIVER_ID));

        mockMvc.perform(get(
                        "/internal/drivers/by-account/{accountId}",
                        ACCOUNT_ID)
                        .header(
                                InternalApiKeyFilter
                                        .INTERNAL_API_KEY_HEADER,
                                "test-internal-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId")
                        .value(ACCOUNT_ID))
                .andExpect(jsonPath("$.driverId")
                        .value(DRIVER_ID));

        verify(driverProfileService)
                .resolveDriverIdByAccountId(ACCOUNT_ID);
    }

    private RequestPostProcessor driverJwt() {
        return jwt()
                .jwt(token -> token
                        .claim("userId", ACCOUNT_ID)
                        .claim("email", "driver@ridelink.test")
                        .claim("role", "DRIVER"))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_DRIVER"));
    }

    private RequestPostProcessor passengerJwt() {
        return jwt()
                .jwt(token -> token
                        .claim("userId", 99L)
                        .claim("email", "passenger@ridelink.test")
                        .claim("role", "PASSENGER"))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_PASSENGER"));
    }

    private DriverProfileResponse profileResponse(
            AvailabilityStatus availabilityStatus) {

        Instant timestamp =
                Instant.parse("2026-09-28T08:00:00Z");

        return new DriverProfileResponse(
                DRIVER_ID,
                ACCOUNT_ID,
                availabilityStatus,
                "Colombo",
                null,
                null,
                timestamp,
                timestamp);
    }
}
