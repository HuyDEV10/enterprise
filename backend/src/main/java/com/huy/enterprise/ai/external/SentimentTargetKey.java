package com.huy.enterprise.ai.external;

import java.text.Normalizer;
import java.util.Locale;

final class SentimentTargetKey {
    private SentimentTargetKey() {}
    static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
