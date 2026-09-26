package com.example.printerinventory.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
    @Column(name = "user_id", updatable = false)
    private Long userId;
    @Column(nullable = false, length = 100, updatable = false)
    private String username;
    @Column(name = "user_role", nullable = false, length = 20, updatable = false)
    private String userRole;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40, updatable = false)
    private AuditAction action;
    @Enumerated(EnumType.STRING) @Column(name = "entity_type", nullable = false, length = 40, updatable = false)
    private AuditEntityType entityType;
    @Column(name = "entity_id", length = 100, updatable = false)
    private String entityId;
    @Column(name = "entity_identifier", length = 250, updatable = false)
    private String entityIdentifier;
    @Column(nullable = false, length = 1000, updatable = false)
    private String description;
    @Column(name = "old_values", columnDefinition = "TEXT", updatable = false)
    private String oldValues;
    @Column(name = "new_values", columnDefinition = "TEXT", updatable = false)
    private String newValues;

    protected AuditLog() {}

    public AuditLog(Instant occurredAt, Long userId, String username, String userRole,
                    AuditAction action, AuditEntityType entityType, String entityId,
                    String entityIdentifier, String description, String oldValues, String newValues) {
        this.occurredAt = occurredAt; this.userId = userId; this.username = username;
        this.userRole = userRole; this.action = action; this.entityType = entityType;
        this.entityId = entityId; this.entityIdentifier = entityIdentifier;
        this.description = description; this.oldValues = oldValues; this.newValues = newValues;
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getUserRole() { return userRole; }
    public AuditAction getAction() { return action; }
    public AuditEntityType getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getEntityIdentifier() { return entityIdentifier; }
    public String getDescription() { return description; }
    public String getOldValues() { return oldValues; }
    public String getNewValues() { return newValues; }
}
