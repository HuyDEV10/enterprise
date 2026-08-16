package com.huy.enterprise.ai.external;

import com.huy.enterprise.ai.client.AiServiceClient;
import com.huy.enterprise.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExternalEventService {
    private final ExternalEventRepository events;
    private final AiServiceClient aiClient;

    @Transactional(readOnly = true)
    public List<ExternalEventResponse> find(LocalDate from, LocalDate to) {
        return events.findByEventDateBetweenOrderByEventDateDesc(from, to).stream()
                .map(ExternalEventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExternalEventResponse one(UUID id) {
        return ExternalEventResponse.from(require(id));
    }

    @Transactional
    public ExternalEventResponse analyze(UUID id, AnalyzeExternalEventRequest request) {
        ExternalEvent event = require(id);
        AiServiceClient.EntitySentimentResponse result = aiClient.analyzeEntitySentiment(
                request.entity().trim(), request.text().trim());
        EntitySentiment sentiment;
        try {
            sentiment = EntitySentiment.valueOf(result.sentiment().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("AI service returned an unsupported sentiment label: " + result.sentiment(), ex);
        }
        event.setSentiment(sentiment);
        event.setSentimentConfidence(BigDecimal.valueOf(result.confidence()));
        event.setSentimentModelVersion(result.modelVersion());
        return ExternalEventResponse.from(events.save(event));
    }

    private ExternalEvent require(UUID id) {
        return events.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("External event not found: " + id));
    }
}
