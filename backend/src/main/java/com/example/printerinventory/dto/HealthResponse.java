package com.example.printerinventory.dto;

import java.time.Instant;

public record HealthResponse(
        String status,
        String api,
        String database,
        String message,
        Instant checkedAt
) {}
