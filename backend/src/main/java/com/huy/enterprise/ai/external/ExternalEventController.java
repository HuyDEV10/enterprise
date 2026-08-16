package com.huy.enterprise.ai.external;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/external-events")
@RequiredArgsConstructor
public class ExternalEventController {
    private final GdeltIngestionService gdelt;
    private final ExternalEventService service;
    private final ExternalEventImpactService impacts;

    @PostMapping("/gdelt/latest")
    public GdeltIngestionResponse ingestLatest(
            @RequestParam(defaultValue = "") List<String> countryCodes,
            @RequestParam(defaultValue = "14,15,16,17,18,19,20") List<String> eventRootCodes,
            @RequestParam(defaultValue = "100") int limit) {
        return gdelt.ingestLatest(countryCodes, eventRootCodes, limit);
    }

    @GetMapping
    public List<ExternalEventResponse> find(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.find(from, to);
    }

    @GetMapping("/{id}")
    public ExternalEventResponse one(@PathVariable UUID id) {
        return service.one(id);
    }

    @PostMapping("/{id}/sentiment")
    public ExternalEventResponse analyze(
            @PathVariable UUID id,
            @Valid @RequestBody AnalyzeExternalEventRequest request) {
        return service.analyze(id, request);
    }

    @PostMapping("/{id}/impacts/calculate")
    public List<ExternalEventImpactResponse> calculateImpacts(@PathVariable UUID id) {
        return impacts.calculate(id);
    }

    @GetMapping("/{id}/impacts")
    public List<ExternalEventImpactResponse> impacts(@PathVariable UUID id) {
        return impacts.byEvent(id);
    }
}
