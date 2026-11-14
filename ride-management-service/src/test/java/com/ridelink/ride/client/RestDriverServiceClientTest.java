package com.ridelink.ride.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestDriverServiceClientTest {

    private static final String BASE_URL = "http://driver.test";
    private static final String API_KEY = "test-internal-api-key";

    private MockRestServiceServer server;
    private RestDriverServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestDriverServiceClient(builder.baseUrl(BASE_URL).build(), API_KEY);
    }

    @Test
    void constructorUsesPatchCapableJdkRequestFactory() {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        RestClient restClient = mock(RestClient.class);
        when(builder.requestFactory(any(ClientHttpRequestFactory.class))).thenReturn(builder);
        when(builder.baseUrl(BASE_URL)).thenReturn(builder);
        when(builder.build()).thenReturn(restClient);

        new RestDriverServiceClient(
                builder,
                BASE_URL,
                API_KEY,
                Duration.ofMillis(1234),
                Duration.ofMillis(5678)
        );

        var requestFactoryCaptor = org.mockito.ArgumentCaptor.forClass(ClientHttpRequestFactory.class);
        verify(builder).requestFactory(requestFactoryCaptor.capture());
        verify(builder).baseUrl(BASE_URL);
        assertInstanceOf(JdkClientHttpRequestFactory.class, requestFactoryCaptor.getValue());
    }

    @Test
    void resolveDriverIdByAccountSendsApiKeyAndMapsDriverId() {
        server.expect(once(), requestTo(BASE_URL + "/internal/drivers/by-account/901"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(RestDriverServiceClient.INTERNAL_API_KEY_HEADER, API_KEY))
                .andRespond(withSuccess(
                        "{\"accountId\":901,\"driverId\":81}",
                        MediaType.APPLICATION_JSON
                ));

        assertEquals(81L, client.resolveDriverId(901L));
        server.verify();
    }

    @Test
    void resolveDriverIdRejectsMismatchedAccountResponse() {
        server.expect(requestTo(BASE_URL + "/internal/drivers/by-account/901"))
                .andRespond(withSuccess(
                        "{\"accountId\":902,\"driverId\":81}",
                        MediaType.APPLICATION_JSON
                ));

        assertThrows(DriverServiceIntegrationException.class, () -> client.resolveDriverId(901L));
        server.verify();
    }

    @Test
    void eligibilitySendsEncodedServiceAreaAndApiKeyAndMapsDriverId() {
        server.expect(once(), requestTo(BASE_URL + "/internal/drivers/eligible?serviceArea=Colombo%20North"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(RestDriverServiceClient.INTERNAL_API_KEY_HEADER, API_KEY))
                .andRespond(withSuccess("""
                        [
                          {
                            "driverId": 1,
                            "accountId": 10,
                            "serviceArea": "Colombo North",
                            "currentLatitude": 6.934400,
                            "currentLongitude": 79.842800
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        var drivers = client.findEligibleDrivers("Colombo North");

        assertEquals(1, drivers.size());
        assertEquals(1L, drivers.getFirst().driverId());
        assertEquals(10L, drivers.getFirst().accountId());
        server.verify();
    }

    @Test
    void emptyEligibilityResponseReturnsEmptyList() {
        server.expect(requestTo(BASE_URL + "/internal/drivers/eligible?serviceArea=Colombo"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertEquals(0, client.findEligibleDrivers("Colombo").size());
        server.verify();
    }

    @Test
    void reserveSendsPatchForDriverWithUnavailableBody() {
        server.expect(requestTo(BASE_URL + "/internal/drivers/42/availability"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(RestDriverServiceClient.INTERNAL_API_KEY_HEADER, API_KEY))
                .andExpect(content().json("{\"availabilityStatus\":\"UNAVAILABLE\"}"))
                .andRespond(withSuccess());

        client.reserveDriver(42L);

        server.verify();
    }

    @Test
    void reserveConflictIsDistinguishable() {
        server.expect(requestTo(BASE_URL + "/internal/drivers/42/availability"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThrows(DriverReservationConflictException.class, () -> client.reserveDriver(42L));
        server.verify();
    }

    @Test
    void releaseSendsAvailableBody() {
        server.expect(requestTo(BASE_URL + "/internal/drivers/42/availability"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(RestDriverServiceClient.INTERNAL_API_KEY_HEADER, API_KEY))
                .andExpect(content().json("{\"availabilityStatus\":\"AVAILABLE\"}"))
                .andRespond(withSuccess());

        client.releaseDriver(42L);

        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 500})
    void reserveNonConflictHttpErrorsAreIntegrationFailures(int status) {
        server.expect(requestTo(BASE_URL + "/internal/drivers/42/availability"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThrows(DriverServiceIntegrationException.class, () -> client.reserveDriver(42L));
        server.verify();
    }

    @Test
    void transportFailureIsAnIntegrationFailure() {
        RestClient restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory((uri, httpMethod) -> {
                    throw new IOException("Read timed out");
                })
                .build();
        RestDriverServiceClient transportClient = new RestDriverServiceClient(restClient, API_KEY);

        assertThrows(
                DriverServiceIntegrationException.class,
                () -> transportClient.findEligibleDrivers("Colombo")
        );
    }

    @Test
    void missingInternalApiKeyFailsBeforeSendingRequest() {
        RestDriverServiceClient unconfiguredClient = new RestDriverServiceClient(
                RestClient.builder().baseUrl(BASE_URL).build(),
                " "
        );

        assertThrows(
                DriverServiceIntegrationException.class,
                () -> unconfiguredClient.findEligibleDrivers("Colombo")
        );
    }

}
