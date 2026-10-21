package com.huy.enterprise.ai.external;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExternalEventResponse(
        UUID id,
        String externalSource,
        String externalEventId,
        LocalDate eventDate,
        String eventType,
        String eventCode,
        String eventBaseCode,
        String eventRootCode,
        String actor1Name,
        String actor2Name,
        String countryCode,
        String country,
        String location,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal goldsteinScore,
        BigDecimal avgTone,
        Integer numMentions,
        Integer numSources,
        Integer numArticles,
        String sourceUrl,
        EntitySentiment sentiment,
        BigDecimal sentimentConfidence,
        String sentimentModelVersion,
        ExternalEventSignal signal) {

    static ExternalEventResponse from(ExternalEvent event) {
        return new ExternalEventResponse(
                event.getId(), event.getExternalSource(), event.getExternalEventId(), event.getEventDate(),
                event.getEventType(), event.getEventCode(), event.getEventBaseCode(), event.getEventRootCode(),
                event.getActor1Name(), event.getActor2Name(), event.getCountryCode(), event.getCountry(),
                event.getLocation(), event.getLatitude(), event.getLongitude(), event.getGoldsteinScore(),
                event.getAvgTone(), event.getNumMentions(), event.getNumSources(), event.getNumArticles(),
                event.getSourceUrl(), event.getSentiment(), event.getSentimentConfidence(),
                event.getSentimentModelVersion(), event.getSignal());
    }
}
