package com.huy.enterprise.ai.external;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnalyzeExternalEventRequest(
        @NotBlank @Size(max = 300) String entity,
        @NotBlank @Size(max = 20000) String text) {
}
