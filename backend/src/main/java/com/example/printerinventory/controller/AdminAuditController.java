package com.example.printerinventory.controller;

import com.example.printerinventory.dto.*;
import com.example.printerinventory.entity.*;
import com.example.printerinventory.service.AuditService;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditController {
    private final AuditService audit;
    public AdminAuditController(AuditService audit) { this.audit = audit; }

    @GetMapping
    public PageResponse<AuditLogResponse> list(
            @RequestParam(required = false) @Size(max = 100) String username,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return audit.list(username, action, entityType, page, size);
    }
}
