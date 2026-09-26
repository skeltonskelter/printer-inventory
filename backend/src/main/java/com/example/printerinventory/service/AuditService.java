package com.example.printerinventory.service;

import com.example.printerinventory.dto.*;
import com.example.printerinventory.entity.*;
import com.example.printerinventory.repository.AuditLogRepository;
import com.example.printerinventory.security.InventoryUserPrincipal;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true)
public class AuditService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private final AuditLogRepository logs;
    private final ObjectMapper json;

    public AuditService(AuditLogRepository logs, ObjectMapper json) { this.logs = logs; this.json = json; }

    public PageResponse<AuditLogResponse> list(String username, AuditAction action, AuditEntityType entityType,
                                                int page, int size) {
        Specification<AuditLog> spec = (root, query, cb) -> cb.conjunction();
        if (username != null && !username.isBlank()) {
            String match = "%" + username.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("username")), match));
        }
        if (action != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        if (entityType != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("entityType"), entityType));
        var pageResult = logs.findAll(spec, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
        return PageResponse.from(pageResult.map(this::response));
    }

    @Transactional
    public void record(AuditAction action, AuditEntityType entityType, Object entityId,
                       String identifier, String description, Map<String, ?> oldValues, Map<String, ?> newValues) {
        var principal = currentPrincipal();
        save(principal == null ? null : principal.id(), principal == null ? "SYSTEM" : principal.getUsername(),
                principal == null ? "SYSTEM" : principal.role(), action, entityType, entityId,
                identifier, description, oldValues, newValues);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAuthentication(InventoryUserPrincipal principal, AuditAction action) {
        save(principal.id(), principal.getUsername(), principal.role(), action, AuditEntityType.AUTHENTICATION,
                principal.id(), principal.getUsername(), action == AuditAction.LOGIN ? "Successful login." : "User logged out.",
                null, null);
    }

    private void save(Long userId, String username, String role, AuditAction action, AuditEntityType entityType,
                      Object entityId, String identifier, String description,
                      Map<String, ?> oldValues, Map<String, ?> newValues) {
        logs.save(new AuditLog(Instant.now(), userId, username, role, action, entityType,
                entityId == null ? null : entityId.toString(), identifier, description,
                encode(oldValues), encode(newValues)));
    }

    private String encode(Map<String, ?> values) {
        if (values == null || values.isEmpty()) return null;
        try { return json.writeValueAsString(values); }
        catch (JacksonException exception) { throw new IllegalStateException("Audit details could not be serialized.", exception); }
    }

    private Map<String, Object> decode(String values) {
        if (values == null) return Map.of();
        try { return json.readValue(values, MAP_TYPE); }
        catch (JacksonException exception) { throw new IllegalStateException("Stored audit details are invalid.", exception); }
    }

    private AuditLogResponse response(AuditLog value) {
        return new AuditLogResponse(value.getId(), value.getOccurredAt(), value.getUsername(), value.getUserId(),
                value.getUserRole(), value.getAction(), value.getEntityType(), value.getEntityId(),
                value.getEntityIdentifier(), value.getDescription(), decode(value.getOldValues()), decode(value.getNewValues()));
    }

    private static InventoryUserPrincipal currentPrincipal() {
        Object value = SecurityContextHolder.getContext().getAuthentication() == null ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return value instanceof InventoryUserPrincipal principal ? principal : null;
    }
}
