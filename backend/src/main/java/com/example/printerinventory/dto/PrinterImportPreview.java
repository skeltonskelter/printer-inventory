package com.example.printerinventory.dto;

import java.util.List;

public record PrinterImportPreview(long totalRows, long validRows, long invalidRows,
                                   List<ImportRowError> errors) {}
