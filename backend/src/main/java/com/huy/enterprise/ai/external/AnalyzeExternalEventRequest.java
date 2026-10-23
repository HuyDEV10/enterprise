package com.huy.enterprise.ai.external;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record AnalyzeExternalEventRequest(
        @NotBlank @Size(max = 300) String entity,
        @NotBlank @Size(max = 20000) String text,
        UUID supplierId,
        @Pattern(regexp="auto|en|vi") String language) {
    public AnalyzeExternalEventRequest(String entity, String text) { this(entity,text,null,"auto"); }
}
