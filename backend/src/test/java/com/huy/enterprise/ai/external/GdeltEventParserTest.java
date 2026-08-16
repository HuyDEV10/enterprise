package com.huy.enterprise.ai.external;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GdeltEventParserTest {
    @Test
    void parsesDocumentedEventColumnsWithoutInventingValues() {
        String[] columns = new String[61];
        java.util.Arrays.fill(columns, "");
        columns[0] = "123456";
        columns[1] = "20260816";
        columns[6] = "ACTOR ONE";
        columns[16] = "ACTOR TWO";
        columns[26] = "141";
        columns[27] = "141";
        columns[28] = "14";
        columns[30] = "-6.5";
        columns[31] = "4";
        columns[32] = "3";
        columns[33] = "3";
        columns[34] = "-2.1";
        columns[52] = "Technical fixture location";
        columns[53] = "XX";
        columns[56] = "10.5";
        columns[57] = "106.7";
        columns[60] = "https://example.invalid/article";

        GdeltEventRecord record = GdeltEventParser.parse(String.join("\t", columns));

        assertEquals("123456", record.externalEventId());
        assertEquals("14", record.eventRootCode());
        assertEquals("XX", record.countryCode());
        assertEquals("https://example.invalid/article", record.sourceUrl());
    }
}
