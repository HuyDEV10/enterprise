package com.huy.enterprise.ai.client;

import com.huy.enterprise.ai.AiServiceUnavailableException;
import com.huy.enterprise.ai.config.AiServiceProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.List;

@Component
public class AiServiceClient {
    private final AiServiceProperties properties;
    private final RestClient restClient;

    public AiServiceClient(AiServiceProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public DemandForecastResponse forecastDemand(List<DemandHistoryPoint> history, LocalDate forecastStartDate) {
        ensureEnabled();
        try {
            DemandForecastResponse response = restClient.post()
                    .uri("/api/v1/forecast/demand")
                    .body(new DemandForecastRequest(history, forecastStartDate))
                    .retrieve()
                    .body(DemandForecastResponse.class);
            if (response == null) {
                throw new AiServiceUnavailableException("AI service returned an empty demand forecast response");
            }
            return response;
        } catch (RestClientException ex) {
            throw new AiServiceUnavailableException("Demand forecast service is unavailable", ex);
        }
    }

    public M5SeriesResponse loadM5Series(String itemId, String storeId) {
        ensureEnabled();
        try {
            M5SeriesResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/datasets/m5/series/{itemId}")
                            .queryParam("storeId", storeId)
                            .build(itemId))
                    .retrieve()
                    .body(M5SeriesResponse.class);
            if (response == null) {
                throw new AiServiceUnavailableException("AI service returned an empty M5 series response");
            }
            return response;
        } catch (RestClientException ex) {
            throw new AiServiceUnavailableException("M5 dataset service is unavailable", ex);
        }
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new AiServiceUnavailableException("AI service integration is disabled");
        }
    }

    public record DemandHistoryPoint(LocalDate date, double quantity) {
    }

    public record DemandForecastRequest(List<DemandHistoryPoint> history, LocalDate forecastStartDate) {
    }

    public record DemandForecastPoint(LocalDate date, double quantity) {
    }

    public record DemandForecastSummary(double next7Days, double next14Days, double next28Days) {
    }

    public record DemandForecastResponse(
            int historyDays,
            String modelVersion,
            List<DemandForecastPoint> forecast,
            DemandForecastSummary summary) {
    }

    public record M5DemandPoint(LocalDate date, double quantity, Double sellPrice, String sourceDayKey) {
    }

    public record M5SeriesResponse(
            String seriesId,
            String itemId,
            String storeId,
            String departmentId,
            String categoryId,
            String stateId,
            List<M5DemandPoint> history) {
    }
}
