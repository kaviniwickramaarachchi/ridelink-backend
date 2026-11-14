package com.ridelink.ride.controller;

import com.ridelink.ride.client.DriverServiceIntegrationException;
import com.ridelink.ride.domain.RideStatus;
import com.ridelink.ride.domain.Ride;
import com.ridelink.ride.dto.CreateRideRequest;
import com.ridelink.ride.dto.LocationResponse;
import com.ridelink.ride.dto.RideResponse;
import com.ridelink.ride.exception.DriverIdentityUnavailableException;
import com.ridelink.ride.exception.GlobalExceptionHandler;
import com.ridelink.ride.exception.InvalidRideTransitionException;
import com.ridelink.ride.exception.RideNotFoundException;
import com.ridelink.ride.exception.PassengerIdentityUnavailableException;
import com.ridelink.ride.security.CurrentDriverProvider;
import com.ridelink.ride.security.CurrentPassengerProvider;
import com.ridelink.ride.service.RideService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RideController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class RideControllerTest {

    private static final String ENDPOINT = "/api/v1/rides";
    private static final Long PASSENGER_ID = 42L;
    private static final Long RIDE_ID = 101L;
    private static final Instant CREATED_AT = Instant.parse("2026-09-29T04:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RideService rideService;

    @MockitoBean
    private CurrentPassengerProvider currentPassengerProvider;

    @MockitoBean
    private CurrentDriverProvider currentDriverProvider;

    @Test
    void createRideReturnsCreatedRide() throws Exception {
        when(currentPassengerProvider.getCurrentPassengerId()).thenReturn(PASSENGER_ID);
        when(rideService.createRide(eq(PASSENGER_ID), any(CreateRideRequest.class)))
                .thenReturn(rideResponse());

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/rides/101"))
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.passengerId").value(42))
                .andExpect(jsonPath("$.driverId").doesNotExist())
                .andExpect(jsonPath("$.serviceArea").value("Colombo"))
                .andExpect(jsonPath("$.pickup.placeName").value("Colombo Fort"))
                .andExpect(jsonPath("$.pickup.latitude").value(6.9344))
                .andExpect(jsonPath("$.pickup.longitude").value(79.8428))
                .andExpect(jsonPath("$.destination.placeName").value("Bambalapitiya"))
                .andExpect(jsonPath("$.destination.latitude").value(6.8935))
                .andExpect(jsonPath("$.destination.longitude").value(79.8552))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.requestedAt").value("2026-09-29T04:00:00Z"))
                .andExpect(jsonPath("$.assignedAt").doesNotExist())
                .andExpect(jsonPath("$.acceptedAt").doesNotExist())
                .andExpect(jsonPath("$.startedAt").doesNotExist())
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.cancelledAt").doesNotExist())
                .andExpect(jsonPath("$.actualDistance").doesNotExist())
                .andExpect(jsonPath("$.createdAt").value("2026-09-29T04:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-09-29T04:00:00Z"));

        verify(currentPassengerProvider).getCurrentPassengerId();
        ArgumentCaptor<CreateRideRequest> requestCaptor = ArgumentCaptor.forClass(CreateRideRequest.class);
        verify(rideService).createRide(eq(PASSENGER_ID), requestCaptor.capture());
        assertEquals("Colombo", requestCaptor.getValue().serviceArea());
        assertEquals("Colombo Fort", requestCaptor.getValue().pickup().placeName());
        assertEquals("Bambalapitiya", requestCaptor.getValue().destination().placeName());
    }

    @Test
    void missingServiceAreaReturnsBadRequest() throws Exception {
        assertInvalidRequest(
                validRequest().replace("\"serviceArea\": \"Colombo\",", ""),
                "serviceArea"
        );
    }

    @Test
    void blankServiceAreaReturnsBadRequest() throws Exception {
        assertInvalidRequest(
                validRequest().replace("\"serviceArea\": \"Colombo\"", "\"serviceArea\": \" \""),
                "serviceArea"
        );
    }

    @Test
    void serviceAreaLongerThanMaximumReturnsBadRequest() throws Exception {
        assertInvalidRequest(
                validRequest().replace("Colombo", "a".repeat(101)),
                "serviceArea"
        );
    }
    @Test
    void missingPickupReturnsBadRequest() throws Exception {
        assertInvalidRequest("""
                {
                  "serviceArea": "Colombo",
                  "destination": {
                    "placeName": "Bambalapitiya",
                    "latitude": 6.8935,
                    "longitude": 79.8552
                  }
                }
                """, "pickup");
    }

    @Test
    void missingDestinationReturnsBadRequest() throws Exception {
        assertInvalidRequest("""
                {
                  "serviceArea": "Colombo",
                  "pickup": {
                    "placeName": "Colombo Fort",
                    "latitude": 6.9344,
                    "longitude": 79.8428
                  }
                }
                """, "destination");
    }

    @Test
    void blankPickupPlaceNameReturnsBadRequest() throws Exception {
        assertInvalidRequest(requestWithPickup(" ", "6.9344", "79.8428"), "pickup.placeName");
    }

    @Test
    void latitudeBelowMinimumReturnsBadRequest() throws Exception {
        assertInvalidRequest(requestWithPickup("Colombo Fort", "-90.0001", "79.8428"), "pickup.latitude");
    }

    @Test
    void latitudeAboveMaximumReturnsBadRequest() throws Exception {
        assertInvalidRequest(requestWithPickup("Colombo Fort", "90.0001", "79.8428"), "pickup.latitude");
    }

    @Test
    void longitudeBelowMinimumReturnsBadRequest() throws Exception {
        assertInvalidRequest(requestWithPickup("Colombo Fort", "6.9344", "-180.0001"), "pickup.longitude");
    }

    @Test
    void longitudeAboveMaximumReturnsBadRequest() throws Exception {
        assertInvalidRequest(requestWithPickup("Colombo Fort", "6.9344", "180.0001"), "pickup.longitude");
    }

    @Test
    void unknownPassengerIdPropertyReturnsBadRequest() throws Exception {
        String request = validRequest().replaceFirst("\\{", "{\"passengerId\": 999,");

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed or unreadable JSON"))
                .andExpect(jsonPath("$.path").value(ENDPOINT));

        verifyNoInteractions(rideService);
    }

    @Test
    void placeNameLongerThanMaximumReturnsBadRequest() throws Exception {
        assertInvalidRequest(requestWithPickup("a".repeat(256), "6.9344", "79.8428"), "pickup.placeName");
    }

    @Test
    void exactCoordinateBoundariesAreAccepted() throws Exception {
        when(currentPassengerProvider.getCurrentPassengerId()).thenReturn(PASSENGER_ID);
        when(rideService.createRide(eq(PASSENGER_ID), any(CreateRideRequest.class)))
                .thenReturn(rideResponse());

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(boundaryRequest()))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateRideRequest> requestCaptor = ArgumentCaptor.forClass(CreateRideRequest.class);
        verify(rideService).createRide(eq(PASSENGER_ID), requestCaptor.capture());
        CreateRideRequest request = requestCaptor.getValue();
        assertAll(
                () -> assertEquals(new BigDecimal("-90"), request.pickup().latitude()),
                () -> assertEquals(new BigDecimal("-180"), request.pickup().longitude()),
                () -> assertEquals(new BigDecimal("90"), request.destination().latitude()),
                () -> assertEquals(new BigDecimal("180"), request.destination().longitude())
        );
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pickup\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed or unreadable JSON"))
                .andExpect(jsonPath("$.path").value(ENDPOINT));

        verifyNoInteractions(rideService);
    }

    @Test
    void unavailablePassengerIdentityReturnsUnauthorized() throws Exception {
        when(currentPassengerProvider.getCurrentPassengerId())
                .thenThrow(new PassengerIdentityUnavailableException(
                        "Authenticated passenger identity is unavailable"
                ));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message")
                        .value("Authenticated passenger identity is unavailable"))
                .andExpect(jsonPath("$.path").value(ENDPOINT));

        verifyNoInteractions(rideService);
    }

    @Test
    void acceptRideUsesCurrentDriver() throws Exception {
        when(currentDriverProvider.getCurrentDriverId()).thenReturn(77L);
        when(rideService.acceptRide(RIDE_ID, 77L)).thenReturn(rideResponse());

        mockMvc.perform(patch(ENDPOINT + "/{rideId}/accept", RIDE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101));

        verify(currentDriverProvider).getCurrentDriverId();
        verify(rideService).acceptRide(RIDE_ID, 77L);
    }

    @Test
    void startRideUsesCurrentDriver() throws Exception {
        when(currentDriverProvider.getCurrentDriverId()).thenReturn(77L);
        when(rideService.startRide(RIDE_ID, 77L)).thenReturn(rideResponse());

        mockMvc.perform(patch(ENDPOINT + "/{rideId}/start", RIDE_ID))
                .andExpect(status().isOk());

        verify(currentDriverProvider).getCurrentDriverId();
        verify(rideService).startRide(RIDE_ID, 77L);
    }

    @Test
    void completeRideUsesCurrentDriverAndDistance() throws Exception {
        when(currentDriverProvider.getCurrentDriverId()).thenReturn(77L);
        when(rideService.completeRide(RIDE_ID, 77L, new BigDecimal("12.345")))
                .thenReturn(rideResponse());

        mockMvc.perform(patch(ENDPOINT + "/{rideId}/complete", RIDE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualDistance\":12.345}"))
                .andExpect(status().isOk());

        verify(currentDriverProvider).getCurrentDriverId();
        verify(rideService).completeRide(RIDE_ID, 77L, new BigDecimal("12.345"));
    }

    @Test
    void completeRideWithoutDistanceReturnsBadRequest() throws Exception {
        assertInvalidPatch(ENDPOINT + "/101/complete", "{}", "actualDistance");
    }

    @Test
    void completeRideWithZeroDistanceReturnsBadRequest() throws Exception {
        assertInvalidPatch(ENDPOINT + "/101/complete", "{\"actualDistance\":0}", "actualDistance");
    }

    @Test
    void completeRideWithNegativeDistanceReturnsBadRequest() throws Exception {
        assertInvalidPatch(ENDPOINT + "/101/complete", "{\"actualDistance\":-1}", "actualDistance");
    }

    @Test
    void completeRideWithTooManyFractionalDigitsReturnsBadRequest() throws Exception {
        assertInvalidPatch(ENDPOINT + "/101/complete", "{\"actualDistance\":12.3456}", "actualDistance");
    }

    @Test
    void cancelRideUsesCurrentPassenger() throws Exception {
        when(currentPassengerProvider.getCurrentPassengerId()).thenReturn(PASSENGER_ID);
        when(rideService.cancelRide(RIDE_ID, PASSENGER_ID)).thenReturn(rideResponse());

        mockMvc.perform(patch(ENDPOINT + "/{rideId}/cancel", RIDE_ID))
                .andExpect(status().isOk());

        verify(currentPassengerProvider).getCurrentPassengerId();
        verify(rideService).cancelRide(RIDE_ID, PASSENGER_ID);
    }

    @Test
    void getRideUsesCurrentPassenger() throws Exception {
        when(currentPassengerProvider.getCurrentPassengerId()).thenReturn(PASSENGER_ID);
        when(rideService.getRide(RIDE_ID, PASSENGER_ID)).thenReturn(rideResponse());

        mockMvc.perform(get(ENDPOINT + "/{rideId}", RIDE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101));

        verify(currentPassengerProvider).getCurrentPassengerId();
        verify(rideService).getRide(RIDE_ID, PASSENGER_ID);
    }

    @Test
    void listRidesUsesCurrentPassenger() throws Exception {
        when(currentPassengerProvider.getCurrentPassengerId()).thenReturn(PASSENGER_ID);
        when(rideService.listPassengerRides(PASSENGER_ID)).thenReturn(List.of(rideResponse()));

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101));

        verify(currentPassengerProvider).getCurrentPassengerId();
        verify(rideService).listPassengerRides(PASSENGER_ID);
    }

    @Test
    void listRidesReturnsEmptyArray() throws Exception {
        when(currentPassengerProvider.getCurrentPassengerId()).thenReturn(PASSENGER_ID);
        when(rideService.listPassengerRides(PASSENGER_ID)).thenReturn(List.of());

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listDriverRidesUsesResolvedCurrentDriver() throws Exception {
        when(currentDriverProvider.getCurrentDriverId()).thenReturn(77L);
        when(rideService.listDriverRides(77L)).thenReturn(List.of(rideResponse()));

        mockMvc.perform(get(ENDPOINT + "/driver"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101));

        verify(currentDriverProvider).getCurrentDriverId();
        verify(rideService).listDriverRides(77L);
    }

    @Test
    void getDriverRideUsesResolvedCurrentDriver() throws Exception {
        when(currentDriverProvider.getCurrentDriverId()).thenReturn(77L);
        when(rideService.getDriverRide(RIDE_ID, 77L)).thenReturn(rideResponse());

        mockMvc.perform(get(ENDPOINT + "/driver/{rideId}", RIDE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101));

        verify(currentDriverProvider).getCurrentDriverId();
        verify(rideService).getDriverRide(RIDE_ID, 77L);
    }

    @Test
    void rideNotFoundReturnsNotFound() throws Exception {
        when(currentPassengerProvider.getCurrentPassengerId()).thenReturn(PASSENGER_ID);
        when(rideService.getRide(RIDE_ID, PASSENGER_ID)).thenThrow(new RideNotFoundException(RIDE_ID));

        mockMvc.perform(get(ENDPOINT + "/{rideId}", RIDE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value(ENDPOINT + "/101"));
    }

    @Test
    void invalidTransitionReturnsConflict() throws Exception {
        when(currentDriverProvider.getCurrentDriverId()).thenReturn(77L);
        when(rideService.acceptRide(RIDE_ID, 77L))
                .thenThrow(new InvalidRideTransitionException(RideStatus.REQUESTED, RideStatus.ACCEPTED));

        mockMvc.perform(patch(ENDPOINT + "/{rideId}/accept", RIDE_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.path").value(ENDPOINT + "/101/accept"));
    }

    @Test
    void optimisticLockConflictReturnsSafeConflictResponse() throws Exception {
        when(currentDriverProvider.getCurrentDriverId()).thenReturn(77L);
        when(rideService.acceptRide(RIDE_ID, 77L))
                .thenThrow(new ObjectOptimisticLockingFailureException(Ride.class, RIDE_ID));

        mockMvc.perform(patch(ENDPOINT + "/{rideId}/accept", RIDE_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Ride was modified by another request; reload and retry"))
                .andExpect(jsonPath("$.path").value(ENDPOINT + "/101/accept"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void driverServiceIntegrationFailureReturnsServiceUnavailable() throws Exception {
        when(currentPassengerProvider.getCurrentPassengerId()).thenReturn(PASSENGER_ID);
        when(rideService.createRide(eq(PASSENGER_ID), any(CreateRideRequest.class)))
                .thenThrow(new DriverServiceIntegrationException(
                        "Driver eligibility request failed at http://driver.internal using INTERNAL_API_KEY after Read timed out"
                ));

        MvcResult result = mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("Driver Service is temporarily unavailable"))
                .andExpect(jsonPath("$.path").value(ENDPOINT))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andReturn();

        assertResponseDoesNotExposeSensitiveDetails(result);
    }

    @Test
    void driverIdentityResolutionIntegrationFailureReturnsServiceUnavailable() throws Exception {
        when(currentDriverProvider.getCurrentDriverId())
                .thenThrow(new DriverServiceIntegrationException(
                        "Driver identity request failed at http://driver.internal with INTERNAL_API_KEY and Connect timed out"
                ));

        MvcResult result = mockMvc.perform(get(ENDPOINT + "/driver"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("Driver Service is temporarily unavailable"))
                .andExpect(jsonPath("$.path").value(ENDPOINT + "/driver"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andReturn();

        assertResponseDoesNotExposeSensitiveDetails(result);
        verifyNoInteractions(rideService);
    }

    @Test
    void unexpectedExceptionReturnsSanitizedInternalServerError() throws Exception {
        when(currentPassengerProvider.getCurrentPassengerId()).thenReturn(PASSENGER_ID);
        when(rideService.getRide(RIDE_ID, PASSENGER_ID))
                .thenThrow(new IllegalStateException(
                        "SQL connection failed for jdbc:mysql://db.internal with INTERNAL_API_KEY and stack trace"
                ));

        MvcResult result = mockMvc.perform(get(ENDPOINT + "/{rideId}", RIDE_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected server error occurred"))
                .andExpect(jsonPath("$.path").value(ENDPOINT + "/101"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andReturn();

        assertResponseDoesNotExposeSensitiveDetails(result);
    }

    @Test
    void unavailableDriverIdentityReturnsUnauthorized() throws Exception {
        when(currentDriverProvider.getCurrentDriverId())
                .thenThrow(new DriverIdentityUnavailableException("Authenticated driver identity is unavailable"));

        mockMvc.perform(patch(ENDPOINT + "/{rideId}/accept", RIDE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authenticated driver identity is unavailable"));

        verifyNoInteractions(rideService);
    }

    private void assertResponseDoesNotExposeSensitiveDetails(MvcResult result) throws Exception {
        String responseBody = result.getResponse().getContentAsString();

        assertFalse(responseBody.contains("http://"));
        assertFalse(responseBody.contains("jdbc:"));
        assertFalse(responseBody.contains("INTERNAL_API_KEY"));
        assertFalse(responseBody.contains("Read timed out"));
        assertFalse(responseBody.contains("Connect timed out"));
        assertFalse(responseBody.contains("SQL"));
        assertFalse(responseBody.contains("IllegalStateException"));
        assertFalse(responseBody.contains("DriverServiceIntegrationException"));
        assertFalse(responseBody.contains("stack trace"));
    }

    private void assertInvalidPatch(String endpoint, String content, String field) throws Exception {
        mockMvc.perform(patch(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field));

        verifyNoInteractions(rideService);
    }
    private void assertInvalidRequest(String content, String field) throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value(ENDPOINT))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field));

        verifyNoInteractions(rideService);
    }

    private String validRequest() {
        return requestWithPickup("Colombo Fort", "6.9344", "79.8428");
    }

    private String boundaryRequest() {
        return """
                {
                  "serviceArea": "Colombo",
                  "pickup": {
                    "placeName": "Southwest Boundary",
                    "latitude": -90,
                    "longitude": -180
                  },
                  "destination": {
                    "placeName": "Northeast Boundary",
                    "latitude": 90,
                    "longitude": 180
                  }
                }
                """;
    }

    private String requestWithPickup(String placeName, String latitude, String longitude) {
        return """
                {
                  "serviceArea": "Colombo",
                  "pickup": {
                    "placeName": "%s",
                    "latitude": %s,
                    "longitude": %s
                  },
                  "destination": {
                    "placeName": "Bambalapitiya",
                    "latitude": 6.8935,
                    "longitude": 79.8552
                  }
                }
                """.formatted(placeName, latitude, longitude);
    }

    private RideResponse rideResponse() {
        return new RideResponse(
                RIDE_ID,
                PASSENGER_ID,
                null,
                "Colombo",
                new LocationResponse(
                        "Colombo Fort",
                        new BigDecimal("6.9344"),
                        new BigDecimal("79.8428")
                ),
                new LocationResponse(
                        "Bambalapitiya",
                        new BigDecimal("6.8935"),
                        new BigDecimal("79.8552")
                ),
                RideStatus.REQUESTED,
                CREATED_AT,
                null,
                null,
                null,
                null,
                null,
                null,
                CREATED_AT,
                CREATED_AT
        );
    }
}
