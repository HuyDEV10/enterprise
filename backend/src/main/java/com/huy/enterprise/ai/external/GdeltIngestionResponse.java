package com.huy.enterprise.ai.external;

public record GdeltIngestionResponse(
        String sourceFile,
        int rowsRead,
        int rowsMatched,
        int eventsCreated,
        int eventsUpdated) {
}
