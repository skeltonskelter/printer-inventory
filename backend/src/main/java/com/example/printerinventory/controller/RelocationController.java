package com.example.printerinventory.controller;

import com.example.printerinventory.dto.*;
import com.example.printerinventory.service.RelocationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/printers/{printerId}")
public class RelocationController {
    private final RelocationService relocations;

    public RelocationController(RelocationService relocations) { this.relocations = relocations; }

    @PostMapping("/relocate")
    public PrinterResponse relocate(@PathVariable @Positive long printerId, @Valid @RequestBody RelocationRequest request) {
        return relocations.relocate(printerId, request);
    }

    @GetMapping("/relocations")
    public List<RelocationResponse> history(@PathVariable @Positive long printerId) {
        return relocations.history(printerId);
    }
}
