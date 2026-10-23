package com.huy.enterprise.ai.external;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class GdeltIngestionService {
    private static final String SOURCE = "GDELT_2_EVENT";
    private static final Pattern OFFICIAL_EXPORT_PATH = Pattern.compile("^/gdeltv2/(\\d{14})\\.export\\.CSV\\.zip$");
    private static final DateTimeFormatter BATCH_TIMESTAMP = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT);
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
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        RestClient client = restClientBuilder.clone().requestFactory(factory).build();
        DownloadedExport download = downloadLatest(client, lastUpdateUrl);
        String exportUrl = download.sourceUrl();
        byte[] zipBytes = download.bytes();

        int rowsRead = 0;
        int rowsMatched = 0;
        int created = 0;
        int updated = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            if (zip.getNextEntry() == null) {
                throw new GdeltUnavailableException("GDELT export ZIP has no entries");
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
            throw new GdeltUnavailableException("Unable to read GDELT event export; retry later", ex);
        }
        return new GdeltIngestionResponse(exportUrl, rowsRead, rowsMatched, created, updated);
    }

    record DownloadedExport(String sourceUrl, byte[] bytes) {}

    static DownloadedExport downloadLatest(RestClient client, String manifestUrl) {
        try {
            String manifest = client.get().uri(manifestUrl).retrieve().body(String.class);
            String advertised;
            try {
                advertised = findExportUrl(manifest);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                throw new GdeltUnavailableException("GDELT export manifest is unavailable or invalid; retry later", ex);
            }
            HttpClientErrorException.NotFound lastMissing = null;
            for (String url : exportCandidates(advertised)) {
                try {
                    byte[] bytes = client.get().uri(url).retrieve().body(byte[].class);
                    if (bytes == null || bytes.length == 0) {
                        throw new GdeltUnavailableException("GDELT event export is empty; retry later");
                    }
                    return new DownloadedExport(url, bytes);
                } catch (HttpClientErrorException.NotFound ex) {
                    // Publication can lag behind lastupdate.txt. Only bounded official
                    // timestamp candidates are retried; all other failures stop here.
                    lastMissing = ex;
                }
            }
            throw new GdeltUnavailableException("GDELT recent event exports are not published yet; retry later", lastMissing);
        } catch (RestClientException ex) {
            throw new GdeltUnavailableException("GDELT feed is temporarily unavailable; retry later", ex);
        }
    }

    static List<String> exportCandidates(String advertised) {
        URI uri = URI.create(advertised);
        var match = OFFICIAL_EXPORT_PATH.matcher(uri.getRawPath() == null ? "" : uri.getRawPath());
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"data.gdeltproject.org".equalsIgnoreCase(uri.getHost())
                || uri.getPort() != -1 || uri.getUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null || !match.matches()) {
            return List.of(advertised);
        }
        LocalDateTime timestamp;
        try {
            timestamp = LocalDateTime.parse(match.group(1), BATCH_TIMESTAMP);
        } catch (DateTimeParseException ex) {
            return List.of(advertised);
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(advertised);
        for (int offset = 1; offset <= 4; offset++) {
            candidates.add("https://data.gdeltproject.org/gdeltv2/"
                    + timestamp.minusMinutes(15L * offset).format(BATCH_TIMESTAMP) + ".export.CSV.zip");
        }
        return List.copyOf(candidates);
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

    static String findExportUrl(String manifest) {
        if (manifest == null) {
            throw new IllegalStateException("GDELT lastupdate manifest is empty");
        }
        return manifest.lines()
                .map(String::trim)
                .filter(line -> line.contains(".export.CSV.zip"))
                .map(line -> line.substring(line.lastIndexOf(' ') + 1))
                .map(GdeltIngestionService::preferOfficialHttps)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("GDELT lastupdate manifest contains no event export"));
    }

    private static String preferOfficialHttps(String value) {
        URI uri = URI.create(value);
        if ("http".equalsIgnoreCase(uri.getScheme())
                && "data.gdeltproject.org".equalsIgnoreCase(uri.getHost())) {
            return "https" + value.substring(value.indexOf(':'));
        }
        return value;
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
