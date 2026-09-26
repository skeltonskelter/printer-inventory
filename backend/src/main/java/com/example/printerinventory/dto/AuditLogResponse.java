package com.example.printerinventory.dto;

import com.example.printerinventory.entity.*;
import java.time.Instant;
import java.util.Map;

public record AuditLogResponse(long id, Instant timestamp, String username, Long userId, String userRole,
        AuditAction action, AuditEntityType entityType, String entityId, String entityIdentifier,
        String description, Map<String, Object> oldValues, Map<String, Object> newValues) {}
