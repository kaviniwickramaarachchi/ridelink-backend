package com.ridelink.ride.client;

import com.ridelink.ride.client.dto.DriverAvailabilityRequest;
import com.ridelink.ride.client.dto.DriverIdResponse;
import com.ridelink.ride.client.dto.EligibleDriverResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Component
public class RestDriverServiceClient implements DriverServiceClient {

    static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String AVAILABLE = "AVAILABLE";
    private static final String UNAVAILABLE = "UNAVAILABLE";

    private final RestClient restClient;
    private final String internalApiKey;

    @Autowired
    public RestDriverServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${driver.service.base-url}") String baseUrl,
            @Value("${driver.service.internal-api-key:}") String internalApiKey,
            @Value("${driver.service.connect-timeout}") Duration connectTimeout,
            @Value("${driver.service.read-timeout}") Duration readTimeout
    ) {
        this.restClient = restClientBuilder
                .requestFactory(driverServiceRequestFactory(connectTimeout, readTimeout))
                .baseUrl(baseUrl)
                .build();
        this.internalApiKey = internalApiKey;
    }

    RestDriverServiceClient(RestClient restClient, String internalApiKey) {
        this.restClient = restClient;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Long resolveDriverId(Long accountId) {
        requireInternalApiKey();

        try {
            DriverIdResponse response = restClient.get()
                    .uri("/internal/drivers/by-account/{accountId}", accountId)
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> {
                        throw integrationFailure("Driver identity request failed", result.getStatusCode());
                    })
                    .body(DriverIdResponse.class);

            if (response == null || !accountId.equals(response.accountId())
                    || response.driverId() == null || response.driverId() <= 0) {
                throw new DriverServiceIntegrationException("Driver identity response was invalid");
            }
            return response.driverId();
        } catch (DriverServiceIntegrationException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new DriverServiceIntegrationException("Driver identity request failed", exception);
        }
    }

    @Override
    public List<EligibleDriverResponse> findEligibleDrivers(String serviceArea) {
        requireInternalApiKey();

        try {
            List<EligibleDriverResponse> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/drivers/eligible")
                            .queryParam("serviceArea", serviceArea)
                            .build())
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> {
                        throw integrationFailure("Driver eligibility request failed", result.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<>() {
                    });

            return response == null ? List.of() : response;
        } catch (DriverServiceIntegrationException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new DriverServiceIntegrationException("Driver eligibility request failed", exception);
        }
    }

    @Override
    public void reserveDriver(Long driverId) {
        updateAvailability(driverId, UNAVAILABLE, true);
    }

    @Override
    public void releaseDriver(Long driverId) {
        updateAvailability(driverId, AVAILABLE, false);
    }

    private void updateAvailability(Long driverId, String availabilityStatus, boolean reservation) {
        requireInternalApiKey();

        try {
            restClient.patch()
                    .uri("/internal/drivers/{driverId}/availability", driverId)
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(new DriverAvailabilityRequest(availabilityStatus))
                    .retrieve()
                    .onStatus(status -> reservation && status.value() == HttpStatus.CONFLICT.value(),
                            (request, response) -> {
                                throw new DriverReservationConflictException(driverId);
                            })
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw integrationFailure("Driver availability update failed", response.getStatusCode());
                    })
                    .toBodilessEntity();
        } catch (DriverReservationConflictException | DriverServiceIntegrationException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new DriverServiceIntegrationException("Driver availability update failed", exception);
        }
    }

    private void requireInternalApiKey() {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            throw new DriverServiceIntegrationException("Driver Service internal API key is not configured");
        }
    }

    private DriverServiceIntegrationException integrationFailure(String message, HttpStatusCode status) {
        return new DriverServiceIntegrationException(message + " with HTTP status " + status.value());
    }

    static ClientHttpRequestFactory driverServiceRequestFactory(Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }
}
