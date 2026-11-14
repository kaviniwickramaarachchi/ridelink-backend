package com.ridelink.ride.client;

import com.ridelink.ride.client.dto.FareEstimateRequest;
import com.ridelink.ride.client.dto.FareEstimateResponse;
import com.ridelink.ride.client.dto.InternalFinalFareRequest;
import com.ridelink.ride.client.dto.InternalFinalFareResponse;
import com.ridelink.ride.domain.RideLocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;

@Component
public class RestFareServiceClient implements FareServiceClient {

    static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;
    private final String internalApiKey;

    @Autowired
    public RestFareServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${fare.service.base-url}") String baseUrl,
            @Value("${fare.service.internal-api-key:}") String internalApiKey,
            @Value("${fare.service.connect-timeout}") Duration connectTimeout,
            @Value("${fare.service.read-timeout}") Duration readTimeout
    ) {
        this.restClient = restClientBuilder
                .requestFactory(fareServiceRequestFactory(connectTimeout, readTimeout))
                .baseUrl(baseUrl)
                .build();
        this.internalApiKey = internalApiKey;
    }

    RestFareServiceClient(RestClient restClient, String internalApiKey) {
        this.restClient = restClient;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public FareEstimateResponse estimateFare(RideLocation pickup, RideLocation destination) {
        requireInternalApiKey();

        FareEstimateRequest request = new FareEstimateRequest(
                pickup.getLatitude().doubleValue(),
                pickup.getLongitude().doubleValue(),
                destination.getLatitude().doubleValue(),
                destination.getLongitude().doubleValue()
        );

        try {
            FareEstimateResponse response = restClient.post()
                    .uri("/internal/fares/estimate")
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> {
                        throw integrationFailure("Fare estimate request failed", httpResponse.getStatusCode());
                    })
                    .body(FareEstimateResponse.class);

            if (response == null || response.distanceKm() < 0 || response.estimatedFare() < 0) {
                throw new FareServiceIntegrationException("Fare estimate response was invalid");
            }
            return response;
        } catch (FareServiceIntegrationException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new FareServiceIntegrationException("Fare estimate request failed", exception);
        }
    }

    @Override
    public InternalFinalFareResponse createFinalFare(
            Long rideId,
            Long passengerId,
            BigDecimal distanceKm,
            double durationMinutes
    ) {
        requireInternalApiKey();

        InternalFinalFareRequest request = new InternalFinalFareRequest(
                rideId,
                passengerId,
                distanceKm.doubleValue(),
                durationMinutes
        );

        try {
            InternalFinalFareResponse response = restClient.post()
                    .uri("/internal/fares/final")
                    .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> {
                        throw integrationFailure("Final fare request failed", httpResponse.getStatusCode());
                    })
                    .body(InternalFinalFareResponse.class);

            if (response == null || !rideId.equals(response.rideId())
                    || response.distanceKm() == null || response.durationMinutes() == null
                    || response.amount() == null) {
                throw new FareServiceIntegrationException("Final fare response was invalid");
            }
            return response;
        } catch (FareServiceIntegrationException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new FareServiceIntegrationException("Final fare request failed", exception);
        }
    }

    private void requireInternalApiKey() {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            throw new FareServiceIntegrationException("Fare Service internal API key is not configured");
        }
    }

    private FareServiceIntegrationException integrationFailure(String message, HttpStatusCode status) {
        return new FareServiceIntegrationException(message + " with HTTP status " + status.value());
    }

    static ClientHttpRequestFactory fareServiceRequestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }
}
