package com.example.printerinventory.dto;

import java.util.List;

public record PrinterImportResult(long imported, long invalidRows, List<ImportRowError> errors) {}
