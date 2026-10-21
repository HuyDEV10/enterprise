package com.huy.enterprise.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huy.enterprise.ai.*;
import com.huy.enterprise.ai.config.AiServiceProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

@RestClientTest(value = AiServiceClient.class, properties = "app.ai-service.enabled=true")
@AutoConfigureMockRestServiceServer(enabled = false)
@EnableConfigurationProperties(AiServiceProperties.class)
class AiServiceClientTest {
    static final HttpServer server;
    static final AtomicInteger status = new AtomicInteger(200);
    static final AtomicInteger delay = new AtomicInteger();
    static final AtomicReference<String> response = new AtomicReference<>();
    static final AtomicReference<String> request = new AtomicReference<>();
    static {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                try { Thread.sleep(delay.get()); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                try {
                    exchange.sendResponseHeaders(status.get(), bytes.length);
                    exchange.getResponseBody().write(bytes);
                } finally { exchange.close(); }
            });
            server.start();
        } catch (IOException ex) { throw new ExceptionInInitializerError(ex); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.ai-service.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Autowired AiServiceClient client;
    @Autowired ObjectMapper mapper;
    @Autowired RestClient.Builder builder;
    private final LocalDate start = LocalDate.of(2026, 3, 1);
    private final List<AiServiceClient.DemandHistoryPoint> history = IntStream.range(0, 56)
            .mapToObj(i -> new AiServiceClient.DemandHistoryPoint(start.minusDays(56 - i), 2)).toList();

    @BeforeEach
    void successResponse() throws Exception {
        status.set(200);
        delay.set(0);
        response.set(mapper.writeValueAsString(new AiServiceClient.DemandForecastResponse(56, "contract-test",
                IntStream.range(0, 28).mapToObj(i -> new AiServiceClient.DemandForecastPoint(start.plusDays(i), 2.0)).toList(),
                new AiServiceClient.DemandForecastSummary(14.0, 28.0, 56.0))));
    }

    @AfterAll static void stopServer() { server.stop(0); }

    @Test
    void actualHttpPayloadUsesIsoDatesAndForecastContractIsRead() throws Exception {
        var result = client.forecastDemand(history, start);
        JsonNode sent = mapper.readTree(request.get());
        assertThat(sent.path("history").get(0).path("date").isTextual()).isTrue();
        assertThat(sent.path("history").get(0).path("date").asText()).isEqualTo(history.getFirst().date().toString());
        assertThat(sent.path("forecastStartDate").asText()).isEqualTo(start.toString());
        assertThat(result.forecast()).hasSize(28);
    }

    @Test
    void upstreamInputErrorsKeepTheirCodeAndAreNotAvailabilityErrors() {
        status.set(422);
        response.set("{\"detail\":{\"code\":\"INVALID_HISTORY\",\"message\":\"source details\"}}");
        assertThatThrownBy(() -> client.forecastDemand(history, start)).isInstanceOf(AiServiceInputException.class)
                .satisfies(ex -> assertThat(((AiServiceInputException) ex).getCode()).isEqualTo("INVALID_HISTORY"));
    }

    @Test
    void upstreamUnavailableAndInternalFailureAreDistinct() {
        status.set(503);
        response.set("{}");
        assertThatThrownBy(() -> client.forecastDemand(history, start)).isInstanceOf(AiServiceUnavailableException.class);
        status.set(500);
        assertThatThrownBy(() -> client.forecastDemand(history, start)).isInstanceOf(AiServiceProtocolException.class);
    }

    @Test
    void modelAndDatasetUnavailabilityHaveSpecificCodesWithoutReflectingPrivateDetails() {
        status.set(503);
        for (String code : List.of("MODEL_UNAVAILABLE", "M5_DATASET_UNAVAILABLE")) {
            response.set("{\"detail\":{\"code\":\"" + code + "\",\"message\":\"private upstream path\"}}");
            assertThatThrownBy(() -> client.forecastDemand(history, start)).isInstanceOf(AiServiceUnavailableException.class)
                    .hasMessageNotContaining("private upstream path")
                    .satisfies(ex -> assertThat(((AiServiceUnavailableException) ex).getCode()).isEqualTo(code));
        }
    }

    @Test
    void malformedAndInconsistentForecastResponsesAreRejected() throws Exception {
        response.set("not-json");
        assertThatThrownBy(() -> client.forecastDemand(history, start)).isInstanceOf(AiServiceProtocolException.class);
        successResponse();
        var invalid = mapper.readTree(response.get());
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid.path("summary")).put("next28Days", 0);
        response.set(invalid.toString());
        assertThatThrownBy(() -> client.forecastDemand(history, start)).isInstanceOf(AiServiceProtocolException.class);
        response.set("{}");
        assertThatThrownBy(() -> client.forecastDemand(history, start)).isInstanceOf(AiServiceProtocolException.class);
    }

    @Test
    void readTimeoutIsReportedSeparately() {
        var props = new AiServiceProperties();
        props.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        props.setReadTimeoutMs(30);
        delay.set(150);
        var shortTimeout = new AiServiceClient(props, builder, mapper);
        assertThatThrownBy(() -> shortTimeout.forecastDemand(history, start)).isInstanceOf(AiServiceTimeoutException.class);
    }

    @Test
    void missingQuantityCannotBecomeAZeroDemandForecast() throws Exception {
        var invalid = mapper.readTree(response.get());
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid.path("forecast").get(0)).remove("quantity");
        response.set(invalid.toString());
        assertThatThrownBy(() -> client.forecastDemand(history, start)).isInstanceOf(AiServiceProtocolException.class);
    }
}
