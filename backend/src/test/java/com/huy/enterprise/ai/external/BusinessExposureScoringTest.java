package com.huy.enterprise.ai.external;

import com.huy.enterprise.common.enums.ImpactLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessExposureScoringTest {

    @Test
    void severeRelevantEventWithOperationalExposureBecomesHighOrCriticalRisk() {
        BusinessExposureScoring.Result result = BusinessExposureScoring.calculate(
                new BusinessExposureScoring.Input(
                        "20",
                        BigDecimal.valueOf(-8),
                        BigDecimal.valueOf(-7),
                        "VM",
                        "Vietnam",
                        "Ho Chi Minh City, Vietnam",
                        "ACME SUPPLY",
                        null,
                        EntitySentiment.NEGATIVE,
                        "SUP-001",
                        "Acme Supply",
                        "Vietnam",
                        "Ho Chi Minh City",
                        "Ho Chi Minh City, Vietnam",
                        3,
                        2,
                        2,
                        2,
                        1));

        assertTrue(result.relevant());
        assertEquals(ExternalEventSignal.RISK, result.signal());
        assertTrue(result.impactLevel() == ImpactLevel.HIGH
                || result.impactLevel() == ImpactLevel.CRITICAL);
        assertTrue(result.score().doubleValue() >= 55.0);
    }

    @Test
    void unrelatedEventDoesNotCreateBusinessExposureJustBecauseSupplierHasOrders() {
        BusinessExposureScoring.Result result = BusinessExposureScoring.calculate(
                new BusinessExposureScoring.Input(
                        "20",
                        BigDecimal.valueOf(-10),
                        BigDecimal.valueOf(-10),
                        "RS",
                        "Russia",
                        "Moscow, Russia",
                        "UNRELATED ACTOR",
                        null,
                        EntitySentiment.NEGATIVE,
                        "SUP-001",
                        "Acme Supply",
                        "Vietnam",
                        "Da Nang",
                        "Da Nang, Vietnam",
                        5,
                        5,
                        4,
                        4,
                        4));

        assertFalse(result.relevant());
        assertEquals(BigDecimal.ZERO.setScale(2), result.score());
        assertEquals(ImpactLevel.LOW, result.impactLevel());
        assertEquals(ExternalEventSignal.NEUTRAL, result.signal());
    }

    @Test
    void missingSentimentAndCommodityAreRenormalizedInsteadOfTreatedAsZero() {
        BusinessExposureScoring.Result result = BusinessExposureScoring.calculate(
                new BusinessExposureScoring.Input(
                        "18",
                        BigDecimal.valueOf(-6),
                        BigDecimal.valueOf(-5),
                        "VM",
                        "Vietnam",
                        "Vietnam",
                        null,
                        null,
                        EntitySentiment.NOT_ANALYZED,
                        "SUP-001",
                        "Supplier One",
                        "Vietnam",
                        null,
                        null,
                        1,
                        0,
                        1,
                        1,
                        1));

        assertTrue(result.relevant());
        assertTrue(result.availableWeight() < 100.0);
        assertEquals(null, result.components().get("ENTITY_SENTIMENT").factor());
        assertEquals(null, result.components().get("COMMODITY_PRESSURE").factor());
    }
}
