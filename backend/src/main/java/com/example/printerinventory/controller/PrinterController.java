package com.example.printerinventory.controller;

import com.example.printerinventory.dto.*;
import com.example.printerinventory.entity.PrinterStatus;
import com.example.printerinventory.service.PrinterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/printers")
public class PrinterController {
    private final PrinterService printers;

    public PrinterController(PrinterService printers) { this.printers = printers; }

    @GetMapping({"", "/search"})
    public PageResponse<PrinterResponse> list(
            @RequestParam(required = false) @Size(max = 120) String search,
            @RequestParam(required = false) @Size(max = 100) String brand,
            @RequestParam(required = false) @Positive Long locationId,
            @RequestParam(required = false) PrinterStatus status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return printers.list(search, brand, locationId, status, page, size);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @Size(max = 120) String search,
            @RequestParam(required = false) @Size(max = 100) String brand,
            @RequestParam(required = false) @Positive Long locationId,
            @RequestParam(required = false) PrinterStatus status) {
        var filename = "printer-inventory-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(printers.exportCsv(search, brand, locationId, status));
    }

    @GetMapping("/{id}")
    public PrinterResponse get(@PathVariable @Positive long id) { return printers.get(id); }

    @PostMapping
    public ResponseEntity<PrinterResponse> create(@Valid @RequestBody PrinterRequest request) {
        var printer = printers.create(request);
        return ResponseEntity.created(URI.create("/api/printers/" + printer.id())).body(printer);
    }

    @PutMapping("/{id}")
    public PrinterResponse update(@PathVariable @Positive long id, @Valid @RequestBody PrinterRequest request) {
        return printers.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive long id) {
        printers.delete(id);
        return ResponseEntity.noContent().build();
    }
}
