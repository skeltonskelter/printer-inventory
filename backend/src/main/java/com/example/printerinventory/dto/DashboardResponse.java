package com.example.printerinventory.dto;

import com.example.printerinventory.entity.PrinterStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DashboardResponse(long totalPrinters, Map<PrinterStatus, Long> statusCounts,
                                long relocatedPrinters, List<PrinterResponse> recentlyAdded,
                                List<RecentTransfer> recentTransfers, Instant checkedAt) {
    public record RecentTransfer(PrinterResponse printer, RelocationResponse relocation) {}
}
