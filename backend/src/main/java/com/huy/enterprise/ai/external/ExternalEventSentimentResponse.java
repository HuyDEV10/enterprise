package com.huy.enterprise.ai.external;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ExternalEventSentimentResponse(UUID id, UUID externalEventId, String entity,
        UUID supplierId, String supplierCode, String supplierName, EntitySentiment sentiment,
        BigDecimal confidence, Map<String,Double> probabilities, String language, boolean needsReview,
        String reviewReason, String modelVersion, boolean inputTruncated, String sourceTextSha256,
        String matchMethod, OffsetDateTime analyzedAt) {
    static ExternalEventSentimentResponse from(ExternalEventSentiment value) {
        var supplier = value.getSupplier();
        return new ExternalEventSentimentResponse(value.getId(), value.getExternalEvent().getId(), value.getTargetEntity(),
                supplier == null ? null : supplier.getId(), supplier == null ? null : supplier.getSupplierCode(),
                supplier == null ? null : supplier.getName(), value.getSentiment(), value.getConfidence(), value.getProbabilities(),
                value.getLanguage(), value.isNeedsReview(), value.getReviewReason(), value.getModelVersion(), value.isInputTruncated(),
                value.getSourceTextSha256(), value.getMatchMethod(), value.getAnalyzedAt());
    }
}
