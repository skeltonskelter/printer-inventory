package com.example.printerinventory.service;

import com.example.printerinventory.dto.*;
import com.example.printerinventory.entity.PrinterStatus;
import com.example.printerinventory.repository.*;
import java.time.Clock;
import java.util.EnumMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private final PrinterRepository printers;
    private final RelocationHistoryRepository history;
    private final Clock clock;

    public DashboardService(PrinterRepository printers, RelocationHistoryRepository history, Clock clock) {
        this.printers = printers;
        this.history = history;
        this.clock = clock;
    }

    // Counts and recent activity come from the same database snapshot.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardResponse get() {
        var counts = new EnumMap<PrinterStatus, Long>(PrinterStatus.class);
        for (var status : PrinterStatus.values()) counts.put(status, 0L);
        printers.countLiveByStatus().forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        var added = printers.findTop5ByDeletedAtIsNullOrderByCreatedAtDescIdDesc().stream()
                .map(PrinterResponse::from).toList();
        var transfers = history.findTop5ByPrinterDeletedAtIsNullOrderByCreatedAtDescIdDesc().stream()
                .map(row -> new DashboardResponse.RecentTransfer(PrinterResponse.from(row.getPrinter()),
                        RelocationResponse.from(row))).toList();
        return new DashboardResponse(counts.values().stream().mapToLong(Long::longValue).sum(), counts,
                history.countRelocatedLivePrinters(), added, transfers, clock.instant());
    }
}
