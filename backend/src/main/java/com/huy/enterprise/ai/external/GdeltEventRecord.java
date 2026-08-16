package com.huy.enterprise.ai.external;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GdeltEventRecord(
        String externalEventId,
        LocalDate eventDate,
        String eventCode,
        String eventBaseCode,
        String eventRootCode,
        String actor1Name,
        String actor2Name,
        String countryCode,
        String location,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal goldsteinScore,
        BigDecimal avgTone,
        Integer numMentions,
        Integer numSources,
        Integer numArticles,
        String sourceUrl) {
}
