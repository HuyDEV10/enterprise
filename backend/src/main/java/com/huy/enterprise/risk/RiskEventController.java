package com.huy.enterprise.risk;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/risk-events")
@RequiredArgsConstructor
public class RiskEventController {
    private final RiskEventService riskEventService;

    @GetMapping
    public List<RiskEvent> findAll() {
        return riskEventService.findAll();
    }

    @GetMapping("/{id}")
    public RiskEvent findById(@PathVariable UUID id) {
        return riskEventService.findById(id);
    }

    @PostMapping
    public RiskEvent create(@Valid @RequestBody CreateRiskEventRequest request) {
        return riskEventService.create(request);
    }
}