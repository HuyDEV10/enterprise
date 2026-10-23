package com.huy.enterprise.ai.external;

import com.huy.enterprise.ai.client.AiServiceClient;
import com.huy.enterprise.common.ResourceNotFoundException;
import com.huy.enterprise.common.BusinessException;
import com.huy.enterprise.supplier.Supplier;
import com.huy.enterprise.supplier.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ExternalEventService {
    private final ExternalEventRepository events;
    private final AiServiceClient aiClient;
    private final ExternalEventSentimentRepository sentiments;
    private final SupplierRepository suppliers;

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
        if (!events.existsById(id)) throw new ResourceNotFoundException("External event not found: " + id);
        String target = request.entity().trim();
        String key = SentimentTargetKey.normalize(target);
        if (key.length() > 300) throw new BusinessException("Normalized entity name is too long");
        AiServiceClient.EntitySentimentResponse result = aiClient.analyzeEntitySentiment(
                target, request.text().trim(), request.language() == null ? "auto" : request.language());
        // Lock only after inference; serialize idempotent writes for this event/target.
        ExternalEvent event = events.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("External event not found: " + id));
        EntitySentiment sentiment;
        try {
            sentiment = EntitySentiment.valueOf(result.sentiment().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("AI service returned an unsupported sentiment label: " + result.sentiment(), ex);
        }
        event.setSentiment(sentiment);
        event.setSentimentConfidence(BigDecimal.valueOf(result.confidence()));
        event.setSentimentModelVersion(result.modelVersion());
        Supplier supplier = null;
        String matchMethod = "UNMATCHED";
        if (request.supplierId() != null) {
            supplier = suppliers.findById(request.supplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + request.supplierId()));
            matchMethod = "EXPLICIT_SUPPLIER_SELECTION";
        } else {
            var matched = suppliers.findAll().stream().filter(value ->
                    SentimentTargetKey.normalize(value.getName()).equals(key)
                    || SentimentTargetKey.normalize(value.getSupplierCode()).equals(key)).toList();
            if (matched.size() == 1) { supplier = matched.getFirst(); matchMethod = "EXACT_NAME_OR_CODE"; }
        }
        ExternalEventSentiment analysis = sentiments.findByExternalEventIdAndTargetKey(id,key).orElseGet(ExternalEventSentiment::new);
        analysis.setExternalEvent(event);
        analysis.setSupplier(supplier);
        analysis.setTargetEntity(target);
        analysis.setTargetKey(key);
        analysis.setSentiment(sentiment);
        analysis.setConfidence(BigDecimal.valueOf(result.confidence()));
        analysis.setProbabilities(result.probabilities() == null ? Map.of() : Map.copyOf(result.probabilities()));
        analysis.setLanguage(result.language() == null ? "unknown" : result.language());
        analysis.setNeedsReview(result.requiresReview());
        analysis.setReviewReason(result.requiresReview() && result.reviewReason() == null ? "UNVERIFIED_CONFIDENCE" : result.reviewReason());
        analysis.setModelVersion(result.modelVersion());
        analysis.setInputTruncated(Boolean.TRUE.equals(result.inputTruncated()));
        analysis.setSourceText(request.text().trim());
        analysis.setSourceTextSha256(textHash(request.text().trim()));
        analysis.setMatchMethod(matchMethod);
        analysis.setAnalyzedAt(OffsetDateTime.now());
        sentiments.save(analysis);
        return ExternalEventResponse.from(events.save(event));
    }

    @Transactional(readOnly=true)
    public List<ExternalEventSentimentResponse> sentiments(UUID id) {
        if (!events.existsById(id)) throw new ResourceNotFoundException("External event not found: " + id);
        return sentiments.findByExternalEventIdOrderByAnalyzedAtDesc(id).stream().map(ExternalEventSentimentResponse::from).toList();
    }

    private static String textHash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }

    private ExternalEvent require(UUID id) {
        return events.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("External event not found: " + id));
    }
}
