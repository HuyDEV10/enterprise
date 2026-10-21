package com.huy.enterprise.ai.external;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

final class GdeltEventParser {
    private static final int MIN_COLUMNS = 61;
    private static final DateTimeFormatter SQL_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private GdeltEventParser() {
    }

    static GdeltEventRecord parse(String line) {
        String[] columns = line.split("\\t", -1);
        if (columns.length < MIN_COLUMNS) {
            throw new IllegalArgumentException("GDELT event row has fewer than 61 columns");
        }
        return new GdeltEventRecord(
                columns[0],
                LocalDate.parse(columns[1], SQL_DATE),
                blankToNull(columns[26]),
                blankToNull(columns[27]),
                blankToNull(columns[28]),
                blankToNull(columns[6]),
                blankToNull(columns[16]),
                blankToNull(columns[53]),
                blankToNull(columns[52]),
                decimal(columns[56]),
                decimal(columns[57]),
                decimal(columns[30]),
                decimal(columns[34]),
                integer(columns[31]),
                integer(columns[32]),
                integer(columns[33]),
                blankToNull(columns[60]));
    }

    private static String blankToNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BigDecimal decimal(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : new BigDecimal(normalized);
    }

    private static Integer integer(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : Integer.valueOf(normalized);
    }
}
