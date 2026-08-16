package com.huy.enterprise.ai.external;

import com.huy.enterprise.common.enums.ImpactLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class BusinessExposureScoring {
    public static final double EVENT_SEVERITY_WEIGHT = 25.0;
    public static final double LOCATION_RELEVANCE_WEIGHT = 20.0;
    public static final double SUPPLIER_EXPOSURE_WEIGHT = 15.0;
    public static final double ORDER_EXPOSURE_WEIGHT = 15.0;
    public static final double SHIPMENT_EXPOSURE_WEIGHT = 10.0;
    public static final double INVENTORY_DEPENDENCY_WEIGHT = 5.0;
    public static final double ENTITY_SENTIMENT_WEIGHT = 5.0;
    public static final double COMMODITY_PRESSURE_WEIGHT = 5.0;

    private BusinessExposureScoring() {
    }

    public static Result calculate(Input input) {
        LinkedHashMap<String, Component> components = new LinkedHashMap<>();

        Double severity = eventSeverity(input.eventRootCode(), input.goldsteinScore(), input.avgTone());
        components.put("EVENT_SEVERITY", new Component(
                EVENT_SEVERITY_WEIGHT,
                severity,
                "CAMEO root code plus negative Goldstein/tone when available"));

        Double location = locationRelevance(input);
        components.put("LOCATION_RELEVANCE", new Component(
                LOCATION_RELEVANCE_WEIGHT,
                location,
                location == null
                        ? "Not scored because event or supplier location data is missing"
                        : "Deterministic country/region/address text matching"));

        Double supplierExposure = supplierExposure(input);
        components.put("SUPPLIER_EXPOSURE", new Component(
                SUPPLIER_EXPOSURE_WEIGHT,
                supplierExposure,
                supplierExposure == null
                        ? "Not scored because event actors or supplier identity data is missing"
                        : "Deterministic actor-to-supplier name/code matching; not machine learning"));

        double orderExposure = countExposure(input.activeOrderCount());
        components.put("ORDER_EXPOSURE", new Component(
                ORDER_EXPOSURE_WEIGHT,
                orderExposure,
                "Active purchase orders=" + input.activeOrderCount()));

        double shipmentExposure = countExposure(input.activeShipmentCount());
        components.put("SHIPMENT_EXPOSURE", new Component(
                SHIPMENT_EXPOSURE_WEIGHT,
                shipmentExposure,
                "Active shipments=" + input.activeShipmentCount()));

        Double inventoryDependency = inventoryDependency(
                input.inventoryCoveredProductCount(),
                input.lowStockProductCount());
        components.put("INVENTORY_DEPENDENCY", new Component(
                INVENTORY_DEPENDENCY_WEIGHT,
                inventoryDependency,
                inventoryDependency == null
                        ? "Not scored because affected products have no inventory coverage"
                        : "Low-stock products=" + input.lowStockProductCount()
                                + "/" + input.inventoryCoveredProductCount()
                                + " inventory-covered affected products"));

        Double sentiment = sentimentFactor(input.sentiment());
        components.put("ENTITY_SENTIMENT", new Component(
                ENTITY_SENTIMENT_WEIGHT,
                sentiment,
                sentiment == null
                        ? "Not scored because entity sentiment is NOT_ANALYZED"
                        : "FinEntity entity-level sentiment=" + input.sentiment().name()));

        components.put("COMMODITY_PRESSURE", new Component(
                COMMODITY_PRESSURE_WEIGHT,
                null,
                "Not scored in Phase 6E: no verified product-to-commodity mapping exists yet"));

        double availableWeight = 0.0;
        double weightedValue = 0.0;
        for (Component component : components.values()) {
            if (component.factor() == null) {
                continue;
            }
            double factor = clamp(component.factor());
            availableWeight += component.weight();
            weightedValue += component.weight() * factor;
        }

        boolean relevant = (location != null && location > 0.0)
                || (supplierExposure != null && supplierExposure >= 0.5);

        double normalizedScore = availableWeight == 0.0
                ? 0.0
                : weightedValue / availableWeight * 100.0;
        if (!relevant) {
            normalizedScore = 0.0;
        }

        BigDecimal score = BigDecimal.valueOf(normalizedScore).setScale(2, RoundingMode.HALF_UP);
        ImpactLevel level = impactLevel(normalizedScore);
        ExternalEventSignal signal = relevant
                ? signal(severity, input.goldsteinScore(), input.sentiment())
                : ExternalEventSignal.NEUTRAL;

        return new Result(
                score,
                level,
                signal,
                relevant,
                availableWeight,
                Collections.unmodifiableMap(components));
    }

    static ImpactLevel impactLevel(double score) {
        if (score >= 75.0) {
            return ImpactLevel.CRITICAL;
        }
        if (score >= 55.0) {
            return ImpactLevel.HIGH;
        }
        if (score >= 35.0) {
            return ImpactLevel.MEDIUM;
        }
        return ImpactLevel.LOW;
    }

    private static Double eventSeverity(String rootCode, BigDecimal goldstein, BigDecimal tone) {
        Double root = rootSeverity(rootCode);
        Double negativeGoldstein = negativeScale(goldstein);
        Double negativeTone = negativeScale(tone);

        double weighted = 0.0;
        double weight = 0.0;
        if (root != null) {
            weighted += root * 0.60;
            weight += 0.60;
        }
        if (negativeGoldstein != null) {
            weighted += negativeGoldstein * 0.25;
            weight += 0.25;
        }
        if (negativeTone != null) {
            weighted += negativeTone * 0.15;
            weight += 0.15;
        }
        return weight == 0.0 ? null : clamp(weighted / weight);
    }

    private static Double rootSeverity(String rootCode) {
        if (rootCode == null || rootCode.isBlank()) {
            return null;
        }
        return switch (rootCode.trim()) {
            case "14" -> 0.45;
            case "15" -> 0.55;
            case "16" -> 0.65;
            case "17" -> 0.75;
            case "18" -> 0.85;
            case "19" -> 0.90;
            case "20" -> 1.00;
            default -> null;
        };
    }

    private static Double negativeScale(BigDecimal value) {
        if (value == null) {
            return null;
        }
        double numeric = value.doubleValue();
        if (numeric >= 0.0) {
            return 0.0;
        }
        return clamp(-numeric / 10.0);
    }

    private static Double locationRelevance(Input input) {
        List<String> eventLocations = nonBlank(
                input.eventCountryCode(), input.eventCountry(), input.eventLocation());
        List<String> supplierLocations = nonBlank(
                input.supplierCountry(), input.supplierRegion(), input.supplierAddress());
        if (eventLocations.isEmpty() || supplierLocations.isEmpty()) {
            return null;
        }

        double best = 0.0;
        if (matchesAny(input.supplierCountry(), eventLocations)) {
            best = Math.max(best, 1.0);
        }
        if (matchesAny(input.supplierRegion(), eventLocations)) {
            best = Math.max(best, 0.85);
        }
        if (matchesAny(input.supplierAddress(), eventLocations)) {
            best = Math.max(best, 0.75);
        }
        return best;
    }

    private static Double supplierExposure(Input input) {
        List<String> actors = nonBlank(input.actor1Name(), input.actor2Name());
        List<String> supplierNames = nonBlank(input.supplierName(), input.supplierCode());
        if (actors.isEmpty() || supplierNames.isEmpty()) {
            return null;
        }
        double best = 0.0;
        for (String actor : actors) {
            for (String supplierName : supplierNames) {
                best = Math.max(best, entitySimilarity(actor, supplierName));
            }
        }
        return best;
    }

    private static double entitySimilarity(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isBlank() || b.isBlank()) {
            return 0.0;
        }
        String compactA = a.replace(" ", "");
        String compactB = b.replace(" ", "");
        if (compactA.equals(compactB)) {
            return 1.0;
        }
        if (Math.min(compactA.length(), compactB.length()) >= 4
                && (compactA.contains(compactB) || compactB.contains(compactA))) {
            return 1.0;
        }

        Set<String> aTokens = meaningfulTokens(a);
        Set<String> bTokens = meaningfulTokens(b);
        if (aTokens.isEmpty() || bTokens.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new LinkedHashSet<>(aTokens);
        intersection.retainAll(bTokens);
        double overlap = (double) intersection.size() / Math.min(aTokens.size(), bTokens.size());
        if (overlap >= 0.75) {
            return 0.75;
        }
        if (overlap >= 0.50) {
            return 0.50;
        }
        return 0.0;
    }

    private static Set<String> meaningfulTokens(String value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String token : value.split("\\s+")) {
            if (token.length() >= 3) {
                result.add(token);
            }
        }
        return result;
    }

    private static boolean matchesAny(String supplierValue, List<String> eventValues) {
        if (supplierValue == null || supplierValue.isBlank()) {
            return false;
        }
        String supplier = normalize(supplierValue);
        String compactSupplier = supplier.replace(" ", "");
        for (String eventValue : eventValues) {
            String event = normalize(eventValue);
            String compactEvent = event.replace(" ", "");
            if (compactSupplier.equals(compactEvent)) {
                return true;
            }
            if (Math.min(compactSupplier.length(), compactEvent.length()) >= 4
                    && (compactSupplier.contains(compactEvent) || compactEvent.contains(compactSupplier))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> nonBlank(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return decomposed.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static double countExposure(int count) {
        if (count <= 0) {
            return 0.0;
        }
        if (count == 1) {
            return 0.50;
        }
        if (count == 2) {
            return 0.75;
        }
        return 1.0;
    }

    private static Double inventoryDependency(Integer coveredProducts, Integer lowStockProducts) {
        if (coveredProducts == null || coveredProducts <= 0 || lowStockProducts == null) {
            return null;
        }
        return clamp((double) lowStockProducts / coveredProducts);
    }

    private static Double sentimentFactor(EntitySentiment sentiment) {
        if (sentiment == null || sentiment == EntitySentiment.NOT_ANALYZED) {
            return null;
        }
        return switch (sentiment) {
            case NEGATIVE -> 1.0;
            case NEUTRAL -> 0.35;
            case POSITIVE -> 0.0;
            case NOT_ANALYZED -> null;
        };
    }

    private static ExternalEventSignal signal(
            Double severity,
            BigDecimal goldstein,
            EntitySentiment sentiment) {
        if ((severity != null && severity >= 0.45) || sentiment == EntitySentiment.NEGATIVE) {
            return ExternalEventSignal.RISK;
        }
        if (sentiment == EntitySentiment.POSITIVE
                && goldstein != null
                && goldstein.compareTo(BigDecimal.valueOf(2)) > 0
                && (severity == null || severity < 0.35)) {
            return ExternalEventSignal.OPPORTUNITY_SIGNAL;
        }
        return ExternalEventSignal.NEUTRAL;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record Input(
            String eventRootCode,
            BigDecimal goldsteinScore,
            BigDecimal avgTone,
            String eventCountryCode,
            String eventCountry,
            String eventLocation,
            String actor1Name,
            String actor2Name,
            EntitySentiment sentiment,
            String supplierCode,
            String supplierName,
            String supplierCountry,
            String supplierRegion,
            String supplierAddress,
            int activeOrderCount,
            int activeShipmentCount,
            int affectedProductCount,
            Integer inventoryCoveredProductCount,
            Integer lowStockProductCount) {
    }

    public record Component(double weight, Double factor, String reason) {
    }

    public record Result(
            BigDecimal score,
            ImpactLevel impactLevel,
            ExternalEventSignal signal,
            boolean relevant,
            double availableWeight,
            Map<String, Component> components) {
    }
}
