package com.ridelink.ride.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;

import com.ridelink.ride.domain.RideLocation;

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

class RestFareServiceClientTest {

    private static final String BASE_URL = "http://fare.test";
    private static final String API_KEY = "test-internal-api-key";

    private MockRestServiceServer server;
    private RestFareServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestFareServiceClient(builder.baseUrl(BASE_URL).build(), API_KEY);
    }

    @Test
    void constructorAppliesConfiguredConnectionAndReadTimeouts() {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        RestClient restClient = mock(RestClient.class);
        when(builder.requestFactory(any(ClientHttpRequestFactory.class))).thenReturn(builder);
        when(builder.baseUrl(BASE_URL)).thenReturn(builder);
        when(builder.build()).thenReturn(restClient);

        new RestFareServiceClient(
                builder,
                BASE_URL,
                API_KEY,
                Duration.ofMillis(1234),
                Duration.ofMillis(5678)
        );

        var requestFactoryCaptor = org.mockito.ArgumentCaptor.forClass(ClientHttpRequestFactory.class);
        verify(builder).requestFactory(requestFactoryCaptor.capture());
        verify(builder).baseUrl(BASE_URL);
        assertConfiguredTimeouts(requestFactoryCaptor.getValue(), 1234, 5678);
    }

    @Test
    void estimateSendsCoordinatesAndApiKeyAndParsesResponse() {
        server.expect(once(), requestTo(BASE_URL + "/internal/fares/estimate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(RestFareServiceClient.INTERNAL_API_KEY_HEADER, API_KEY))
                .andExpect(content().json("""
                        {
                          "pickupLatitude": 6.9344,
                          "pickupLongitude": 79.8428,
                          "destinationLatitude": 6.8935,
                          "destinationLongitude": 79.8552
                        }
                        """))
                .andRespond(withSuccess(
                        "{\"distanceKm\":4.75,\"estimatedFare\":480.0}",
                        MediaType.APPLICATION_JSON
                ));

        var response = client.estimateFare(pickup(), destination());

        assertEquals(4.75, response.distanceKm());
        assertEquals(480.0, response.estimatedFare());
        server.verify();
    }

    @Test
    void estimateNonSuccessIsIntegrationFailure() {
        server.expect(requestTo(BASE_URL + "/internal/fares/estimate"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(FareServiceIntegrationException.class,
                () -> client.estimateFare(pickup(), destination()));
        server.verify();
    }

    @Test
    void estimateTransportFailureIsIntegrationFailure() {
        RestClient restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory((uri, httpMethod) -> {
                    throw new IOException("Read timed out");
                })
                .build();
        RestFareServiceClient transportClient = new RestFareServiceClient(restClient, API_KEY);

        assertThrows(FareServiceIntegrationException.class,
                () -> transportClient.estimateFare(pickup(), destination()));
    }

    @Test
    void finalFareSendsPayloadAndApiKeyAndParsesResponse() {
        server.expect(once(), requestTo(BASE_URL + "/internal/fares/final"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(RestFareServiceClient.INTERNAL_API_KEY_HEADER, API_KEY))
                .andExpect(content().json("""
                        {
                          "rideId": 101,
                          "passengerId": 42,
                          "distanceKm": 12.345,
                          "durationMinutes": 15.0
                        }
                        """))
                .andRespond(withSuccess(
                        "{\"rideId\":101,\"distanceKm\":12.345,\"durationMinutes\":15.0,\"amount\":1087.6}",
                        MediaType.APPLICATION_JSON
                ));

        var response = client.createFinalFare(101L, 42L, new BigDecimal("12.345"), 15.0);

        assertEquals(101L, response.rideId());
        assertEquals(12.345, response.distanceKm());
        assertEquals(15.0, response.durationMinutes());
        assertEquals(1087.6, response.amount());
        server.verify();
    }

    @Test
    void finalFareMismatchedRideIdIsRejected() {
        server.expect(requestTo(BASE_URL + "/internal/fares/final"))
                .andRespond(withSuccess(
                        "{\"rideId\":102,\"distanceKm\":12.345,\"durationMinutes\":15.0,\"amount\":1087.6}",
                        MediaType.APPLICATION_JSON
                ));

        assertThrows(FareServiceIntegrationException.class,
                () -> client.createFinalFare(101L, 42L, new BigDecimal("12.345"), 15.0));
        server.verify();
    }

    @Test
    void finalFareNonSuccessIsIntegrationFailure() {
        server.expect(requestTo(BASE_URL + "/internal/fares/final"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(FareServiceIntegrationException.class,
                () -> client.createFinalFare(101L, 42L, new BigDecimal("12.345"), 15.0));
        server.verify();
    }

    @Test
    void blankInternalApiKeyFailsBeforeSendingRequest() {
        RestFareServiceClient unconfiguredClient = new RestFareServiceClient(
                RestClient.builder().baseUrl(BASE_URL).build(),
                " "
        );

        assertThrows(FareServiceIntegrationException.class,
                () -> unconfiguredClient.estimateFare(pickup(), destination()));
        assertThrows(FareServiceIntegrationException.class,
                () -> unconfiguredClient.createFinalFare(101L, 42L, new BigDecimal("12.345"), 15.0));
    }

    private RideLocation pickup() {
        return new RideLocation("Colombo Fort", new BigDecimal("6.9344"), new BigDecimal("79.8428"));
    }

    private RideLocation destination() {
        return new RideLocation("Bambalapitiya", new BigDecimal("6.8935"), new BigDecimal("79.8552"));
    }

    private static void assertConfiguredTimeouts(
            ClientHttpRequestFactory requestFactory,
            int expectedConnectTimeoutMillis,
            int expectedReadTimeoutMillis
    ) {
        SimpleClientHttpRequestFactory simpleRequestFactory = assertInstanceOf(
                SimpleClientHttpRequestFactory.class,
                requestFactory
        );
        assertTimeoutFieldMillis(simpleRequestFactory, "connectTimeout", expectedConnectTimeoutMillis);
        assertTimeoutFieldMillis(simpleRequestFactory, "readTimeout", expectedReadTimeoutMillis);
    }

    private static void assertTimeoutFieldMillis(
            SimpleClientHttpRequestFactory requestFactory,
            String fieldName,
            int expectedMillis
    ) {
        Object value = ReflectionTestUtils.getField(requestFactory, fieldName);
        if (value instanceof Duration duration) {
            assertEquals(expectedMillis, duration.toMillis());
        } else {
            assertEquals(expectedMillis, ((Number) value).intValue());
        }
    }
}
