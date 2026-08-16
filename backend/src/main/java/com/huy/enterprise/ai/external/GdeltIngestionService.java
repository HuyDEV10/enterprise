package com.huy.enterprise.ai.external;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class GdeltIngestionService {
    private static final String SOURCE = "GDELT_2_EVENT";
    private final ExternalEventRepository events;
    private final RestClient.Builder restClientBuilder;

    @Value("${app.external-intelligence.gdelt.last-update-url:https://data.gdeltproject.org/gdeltv2/lastupdate.txt}")
    private String lastUpdateUrl;

    @Transactional
    public GdeltIngestionResponse ingestLatest(List<String> countryCodes, List<String> eventRootCodes, int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        Set<String> countries = normalized(countryCodes);
        Set<String> roots = normalized(eventRootCodes);
        RestClient client = restClientBuilder.build();

        String manifest = client.get().uri(lastUpdateUrl).retrieve().body(String.class);
        String exportUrl = findExportUrl(manifest);
        byte[] zipBytes = client.get().uri(exportUrl).retrieve().body(byte[].class);
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalStateException("GDELT latest export is empty");
        }

        int rowsRead = 0;
        int rowsMatched = 0;
        int created = 0;
        int updated = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            if (zip.getNextEntry() == null) {
                throw new IllegalStateException("GDELT latest export ZIP has no entries");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && rowsMatched < limit) {
                    rowsRead++;
                    GdeltEventRecord record;
                    try {
                        record = GdeltEventParser.parse(line);
                    } catch (RuntimeException ignored) {
                        continue;
                    }
                    if (!matches(record, countries, roots)) {
                        continue;
                    }
                    rowsMatched++;
                    ExternalEvent event = events.findByExternalSourceAndExternalEventId(SOURCE, record.externalEventId())
                            .orElse(null);
                    boolean isNew = event == null;
                    if (isNew) {
                        event = new ExternalEvent();
                        event.setExternalSource(SOURCE);
                        event.setExternalEventId(record.externalEventId());
                    }
                    apply(event, record);
                    events.save(event);
                    if (isNew) {
                        created++;
                    } else {
                        updated++;
                    }
                }
            }
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Unable to read GDELT latest export", ex);
        }
        return new GdeltIngestionResponse(exportUrl, rowsRead, rowsMatched, created, updated);
    }

    private static boolean matches(GdeltEventRecord record, Set<String> countries, Set<String> roots) {
        boolean countryMatches = countries.isEmpty()
                || (record.countryCode() != null && countries.contains(record.countryCode().toUpperCase(Locale.ROOT)));
        boolean rootMatches = roots.isEmpty()
                || (record.eventRootCode() != null && roots.contains(record.eventRootCode().toUpperCase(Locale.ROOT)));
        return countryMatches && rootMatches;
    }

    private static Set<String> normalized(List<String> values) {
        Set<String> result = new HashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim().toUpperCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static String findExportUrl(String manifest) {
        if (manifest == null) {
            throw new IllegalStateException("GDELT lastupdate manifest is empty");
        }
        return manifest.lines()
                .map(String::trim)
                .filter(line -> line.contains(".export.CSV.zip"))
                .map(line -> line.substring(line.lastIndexOf(' ') + 1))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("GDELT lastupdate manifest contains no event export"));
    }

    private static void apply(ExternalEvent event, GdeltEventRecord record) {
        event.setEventDate(record.eventDate());
        event.setEventCode(record.eventCode());
        event.setEventBaseCode(record.eventBaseCode());
        event.setEventRootCode(record.eventRootCode());
        event.setEventType(record.eventCode() == null ? "CAMEO_UNKNOWN" : "CAMEO_" + record.eventCode());
        event.setActor1Name(record.actor1Name());
        event.setActor2Name(record.actor2Name());
        event.setCountryCode(record.countryCode());
        event.setLocation(record.location());
        event.setLatitude(record.latitude());
        event.setLongitude(record.longitude());
        event.setGoldsteinScore(record.goldsteinScore());
        event.setAvgTone(record.avgTone());
        event.setNumMentions(record.numMentions());
        event.setNumSources(record.numSources());
        event.setNumArticles(record.numArticles());
        event.setSourceUrl(record.sourceUrl());
    }
}
