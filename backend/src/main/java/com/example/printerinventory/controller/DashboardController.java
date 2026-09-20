package com.example.printerinventory.controller;

import com.example.printerinventory.dto.DashboardResponse;
import com.example.printerinventory.service.DashboardService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboard;
    public DashboardController(DashboardService dashboard) { this.dashboard = dashboard; }

    @GetMapping
    public ResponseEntity<DashboardResponse> get() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dashboard.get());
    }
}
