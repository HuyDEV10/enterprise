package com.huy.enterprise.ai.external;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GdeltManifestTest {
    @Test
    void upgradesOfficialHttpExportWithoutSelectingMentionsOrGkg() {
        String manifest = """
                100 ignored http://data.gdeltproject.org/gdeltv2/20261023000000.mentions.CSV.zip
                200 ignored http://data.gdeltproject.org/gdeltv2/20261023000000.export.CSV.zip
                300 ignored http://data.gdeltproject.org/gdeltv2/20261023000000.gkg.csv.zip
                """;
        assertEquals("https://data.gdeltproject.org/gdeltv2/20261023000000.export.CSV.zip",
                GdeltIngestionService.findExportUrl(manifest));
    }

    @Test
    void preservesOfficialHttpsAndLocalHttpFixtures() {
        for (String url : new String[] {
                "https://data.gdeltproject.org/gdeltv2/20261023000000.export.CSV.zip",
                "http://127.0.0.1:54321/technical.export.CSV.zip",
                "http://localhost:54321/technical.export.CSV.zip",
                "http://data.gdeltproject.org.example.invalid/technical.export.CSV.zip"
        }) {
            assertEquals(url, GdeltIngestionService.findExportUrl("200 ignored " + url));
        }
    }
}
