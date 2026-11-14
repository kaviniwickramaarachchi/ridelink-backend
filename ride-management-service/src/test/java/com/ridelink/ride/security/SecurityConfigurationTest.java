package com.ridelink.ride.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.ridelink.ride.client.DriverServiceClient;
import com.ridelink.ride.service.RideService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "JWT_SECRET=ride-service-test-secret-32-bytes-minimum",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
})
@AutoConfigureMockMvc
class SecurityConfigurationTest {

    private static final String SECRET = "ride-service-test-secret-32-bytes-minimum";
    private static final String RIDES = "/api/v1/rides";
    private static final long ACCOUNT_ID = 42L;
    private static final long DRIVER_ID = 81L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;

    @MockitoBean
    private RideService rideService;

    @MockitoBean
    private DriverServiceClient driverServiceClient;

    private JwtEncoder jwtEncoder;

    @BeforeEach
    void setUp() {
        SecretKeySpec key = new SecretKeySpec(
                SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Test
    void swaggerUiAndApiDocsArePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @Test
    void missingJwtReturnsJsonUnauthorizedWithoutLoginRedirect() throws Exception {
        mockMvc.perform(get(RIDES))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value(RIDES));
    }

    @Test
    void malformedJwtReturnsUnauthorized() throws Exception {
        assertUnauthorized("not-a-jwt");
    }

    @Test
    void tamperedJwtReturnsUnauthorized() throws Exception {
        assertUnauthorized(validToken("PASSENGER") + "x");
    }

    @Test
    void expiredJwtReturnsUnauthorized() throws Exception {
        Instant now = Instant.now();
        assertUnauthorized(token(defaultClaims("PASSENGER"), now.minusSeconds(120), now.minusSeconds(60)));
    }

    @Test
    void invalidRoleIsRejectedDuringAuthentication() throws Exception {
        assertUnauthorized(token(defaultClaims("SUPERUSER"), Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(300)));
    }

    @Test
    void missingRequiredClaimReturnsUnauthorized() throws Exception {
        Map<String, Object> claims = defaultClaims("PASSENGER");
        claims.remove("email");
        assertUnauthorized(token(claims, Instant.now().minusSeconds(5), Instant.now().plusSeconds(300)));
    }

    @Test
    void missingExpirationReturnsUnauthorized() throws Exception {
        assertUnauthorized(tokenWithoutExpiration(defaultClaims("PASSENGER")));
    }

    @Test
    void nonPositiveUserIdReturnsUnauthorized() throws Exception {
        Map<String, Object> claims = defaultClaims("PASSENGER");
        claims.put("userId", 0L);
        claims.put("sub", "0");
        assertUnauthorized(token(claims, Instant.now().minusSeconds(5), Instant.now().plusSeconds(300)));
    }

    @Test
    void subjectAndUserIdMismatchReturnsUnauthorized() throws Exception {
        Map<String, Object> claims = defaultClaims("PASSENGER");
        claims.put("sub", "999");
        assertUnauthorized(token(claims, Instant.now().minusSeconds(5), Instant.now().plusSeconds(300)));
    }

    @Test
    void futureIssuedAtReturnsUnauthorized() throws Exception {
        Instant now = Instant.now();
        assertUnauthorized(token(defaultClaims("PASSENGER"), now.plusSeconds(60), now.plusSeconds(300)));
    }

    @Test
    void passengerJwtUsesAuthenticatedUserIdOnPassengerEndpoint() throws Exception {
        when(rideService.listPassengerRides(ACCOUNT_ID)).thenReturn(List.of());

        mockMvc.perform(get(RIDES).header(HttpHeaders.AUTHORIZATION, bearer(validToken("PASSENGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(rideService).listPassengerRides(ACCOUNT_ID);
    }

    @Test
    void driverIsDeniedPassengerOnlyEndpoint() throws Exception {
        mockMvc.perform(get(RIDES).header(HttpHeaders.AUTHORIZATION, bearer(validToken("DRIVER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verifyNoInteractions(rideService);
    }

    @Test
    void passengerIsDeniedDriverLifecycleEndpoint() throws Exception {
        mockMvc.perform(patch(RIDES + "/101/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken("PASSENGER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(driverServiceClient, rideService);
    }

    @Test
    void driverAccountIdResolvesToDriverIdForLifecycleOperation() throws Exception {
        when(driverServiceClient.resolveDriverId(ACCOUNT_ID)).thenReturn(DRIVER_ID);

        mockMvc.perform(patch(RIDES + "/101/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken("DRIVER"))))
                .andExpect(status().isOk());

        verify(driverServiceClient).resolveDriverId(ACCOUNT_ID);
        verify(rideService).acceptRide(101L, DRIVER_ID);
    }

    @Test
    void driverRideListIsScopedUsingResolvedDriverId() throws Exception {
        when(driverServiceClient.resolveDriverId(ACCOUNT_ID)).thenReturn(DRIVER_ID);
        when(rideService.listDriverRides(DRIVER_ID)).thenReturn(List.of());

        mockMvc.perform(get(RIDES + "/driver")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken("DRIVER"))))
                .andExpect(status().isOk());

        verify(rideService).listDriverRides(DRIVER_ID);
    }

    @Test
    void driverRideGetIsScopedUsingResolvedDriverId() throws Exception {
        when(driverServiceClient.resolveDriverId(ACCOUNT_ID)).thenReturn(DRIVER_ID);

        mockMvc.perform(get(RIDES + "/driver/101")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken("DRIVER"))))
                .andExpect(status().isOk());

        verify(rideService).getDriverRide(101L, DRIVER_ID);
    }

    @Test
    void unsafeManualAssignmentEndpointIsNotUsable() throws Exception {
        mockMvc.perform(patch(RIDES + "/101/assign-driver")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken("PASSENGER")))
                        .contentType("application/json")
                        .content("{\"driverId\":81}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(rideService);
    }

    @Test
    void loginNeverRedirects() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.status").value(401));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PASSENGER", "DRIVER", "ADMIN"})
    void validRolesMapToRoleAuthorities(String role) {
        Jwt jwt = jwtDecoder.decode(validToken(role));

        AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);

        assertEquals(ACCOUNT_ID, authentication.getPrincipal());
        assertEquals(List.of("ROLE_" + role), authentication.getAuthorities().stream()
                .map(Object::toString)
                .toList());
    }

    private void assertUnauthorized(String token) throws Exception {
        mockMvc.perform(get(RIDES).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    private String validToken(String role) {
        Instant now = Instant.now();
        return token(defaultClaims(role), now.minusSeconds(5), now.plusSeconds(300));
    }

    private Map<String, Object> defaultClaims(String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", Long.toString(ACCOUNT_ID));
        claims.put("userId", ACCOUNT_ID);
        claims.put("role", role);
        claims.put("email", "passenger@example.com");
        return claims;
    }

    private String token(Map<String, Object> claims, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuedAt(issuedAt)
                .expiresAt(expiresAt);
        claims.forEach(builder::claim);
        return encode(builder.build());
    }

    private String tokenWithoutExpiration(Map<String, Object> claims) {
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder().issuedAt(Instant.now().minusSeconds(5));
        claims.forEach(builder::claim);
        return encode(builder.build());
    }

    private String encode(JwtClaimsSet claims) {
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims
        )).getTokenValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
