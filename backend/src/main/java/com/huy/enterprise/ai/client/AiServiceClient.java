package com.huy.enterprise.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huy.enterprise.ai.*;
import com.huy.enterprise.ai.config.AiServiceProperties;
import com.huy.enterprise.common.ResourceNotFoundException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

@Component
public class AiServiceClient {
    private final AiServiceProperties properties;
    private final RestClient restClient;
    private final ObjectMapper mapper;

    public AiServiceClient(AiServiceProperties properties, RestClient.Builder builder, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        // The injected builder uses Spring Boot's ISO-date Jackson configuration.
        this.restClient = builder.clone().baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
    }

    public DemandForecastResponse forecastDemand(List<DemandHistoryPoint> history, LocalDate forecastStartDate) {
        DemandForecastResponse response = retrieve(() -> restClient.post().uri("/api/v1/forecast/demand")
                .body(new DemandForecastRequest(history, forecastStartDate)).retrieve(), DemandForecastResponse.class);
        validateForecast(response, history.size(), forecastStartDate);
        return response;
    }

    public M5SeriesResponse loadM5Series(String itemId, String storeId) {
        return retrieve(() -> restClient.get()
                .uri(uri -> uri.path("/api/v1/datasets/m5/series/{itemId}").queryParam("storeId", storeId).build(itemId))
                .retrieve(), M5SeriesResponse.class);
    }

    public EntitySentimentResponse analyzeEntitySentiment(String entity, String text) {
        EntitySentimentResponse result = retrieve(() -> restClient.post().uri("/api/v1/sentiment/entity")
                .body(new EntitySentimentRequest(entity, text)).retrieve(), EntitySentimentResponse.class);
        if (result.entity() == null || !result.entity().equals(entity.trim())
                || result.sentiment() == null || !List.of("POSITIVE", "NEGATIVE", "NEUTRAL").contains(result.sentiment())
                || !Double.isFinite(result.confidence()) || result.confidence() < 0 || result.confidence() > 1
                || result.modelVersion() == null || result.modelVersion().isBlank()) {
            throw new AiServiceProtocolException("AI service returned an invalid sentiment response");
        }
        return result;
    }

    private <T> T retrieve(Supplier<RestClient.ResponseSpec> request, Class<T> type) {
        if (!properties.isEnabled()) throw new AiServiceUnavailableException("AI_DISABLED", "AI service integration is disabled");
        try {
            T response = request.get().onStatus(HttpStatusCode::isError, this::handleError).body(type);
            if (response == null) throw new AiServiceProtocolException("AI service returned an empty response");
            return response;
        } catch (RestClientException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof SocketTimeoutException) {
                    throw new AiServiceTimeoutException("AI service request timed out", ex);
                }
            }
            if (ex instanceof ResourceAccessException) throw new AiServiceUnavailableException("AI service is unreachable", ex);
            throw new AiServiceProtocolException("AI service returned an unreadable response", ex);
        }
    }

    private void handleError(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        if (status == 400 || status == 422) {
            String code = "AI_INPUT_INVALID";
            try {
                var detail = mapper.readTree(response.getBody().readNBytes(16_384)).path("detail");
                String supplied = detail.path("code").asText();
                if (supplied.matches("[A-Z][A-Z0-9_]{0,63}")) code = supplied;
            } catch (IOException ignored) {
                // Never reflect an untrusted upstream response body to the caller.
            }
            throw new AiServiceInputException(code, "AI service rejected the input: " + code);
        }
        if (status == 404) throw new ResourceNotFoundException("Requested AI dataset or resource was not found");
        if (status == 408 || status == 504) throw new AiServiceTimeoutException("AI service request timed out", null);
        if (status == 503) {
            String code = "AI_UNAVAILABLE";
            try {
                String supplied = mapper.readTree(response.getBody().readNBytes(16_384)).path("detail").path("code").asText();
                if (List.of("MODEL_UNAVAILABLE", "M5_DATASET_UNAVAILABLE").contains(supplied)) code = supplied;
            } catch (IOException ignored) { }
            String message = switch (code) {
                case "MODEL_UNAVAILABLE" -> "Required AI model is unavailable";
                case "M5_DATASET_UNAVAILABLE" -> "M5 dataset files are unavailable in the AI service";
                default -> "AI service is temporarily unavailable";
            };
            throw new AiServiceUnavailableException(code, message);
        }
        if (status == 429) throw new AiServiceUnavailableException("AI_RATE_LIMITED", "AI service is temporarily rate limited");
        throw new AiServiceProtocolException("AI service failed to process the request");
    }

    private void validateForecast(DemandForecastResponse response, int historyDays, LocalDate start) {
        if (response.historyDays() != historyDays || response.modelVersion() == null || response.modelVersion().isBlank()
                || response.forecast() == null || response.forecast().size() != 28 || response.summary() == null
                || response.summary().next7Days() == null || response.summary().next14Days() == null || response.summary().next28Days() == null) {
            throw new AiServiceProtocolException("AI service returned an invalid forecast contract");
        }
        double sum = 0;
        for (int i = 0; i < 28; i++) {
            DemandForecastPoint point = response.forecast().get(i);
            if (point == null || point.date() == null || point.quantity() == null || !point.date().equals(start.plusDays(i))
                    || !Double.isFinite(point.quantity()) || point.quantity() < 0) {
                throw new AiServiceProtocolException("AI service returned invalid forecast dates or quantities");
            }
            sum += point.quantity();
            double total = switch (i) {
                case 6 -> response.summary().next7Days();
                case 13 -> response.summary().next14Days();
                case 27 -> response.summary().next28Days();
                default -> sum;
            };
            if (!Double.isFinite(total) || total < 0 || Math.abs(sum - total) > 0.001) {
                throw new AiServiceProtocolException("AI service returned inconsistent forecast totals");
            }
        }
    }

    public record DemandHistoryPoint(LocalDate date, double quantity) {}
    public record DemandForecastRequest(List<DemandHistoryPoint> history, LocalDate forecastStartDate) {}
    public record DemandForecastPoint(LocalDate date, Double quantity) {}
    public record DemandForecastSummary(Double next7Days, Double next14Days, Double next28Days) {}
    public record DemandForecastResponse(int historyDays, String modelVersion, List<DemandForecastPoint> forecast, DemandForecastSummary summary) {}
    public record M5DemandPoint(LocalDate date, Double quantity, Double sellPrice, String sourceDayKey) {}
    public record M5SeriesResponse(String seriesId, String itemId, String storeId, String departmentId, String categoryId, String stateId, List<M5DemandPoint> history) {}
    public record EntitySentimentRequest(String entity, String text) {}
    public record EntitySentimentResponse(String entity, String sentiment, double confidence, String modelVersion) {}
}
