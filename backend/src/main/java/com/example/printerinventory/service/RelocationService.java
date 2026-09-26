package com.example.printerinventory.service;

import com.example.printerinventory.dto.*;
import com.example.printerinventory.entity.*;
import com.example.printerinventory.exception.ApiException;
import com.example.printerinventory.repository.*;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(readOnly = true)
public class RelocationService {
    private final PrinterRepository printers;
    private final LocationService locations;
    private final RelocationHistoryRepository history;
    private final Clock clock;
    private final AuditService audit;

    public RelocationService(PrinterRepository printers, LocationService locations,
                             RelocationHistoryRepository history, Clock clock, AuditService audit) {
        this.printers = printers;
        this.locations = locations;
        this.history = history;
        this.clock = clock;
        this.audit = audit;
    }

    public List<RelocationResponse> history(long printerId) {
        // Retained deleted printers still have a readable audit history.
        if (!printers.existsById(printerId)) throw new ApiException(NOT_FOUND, "Printer not found.");
        return history.findByPrinterIdOrderByRelocationDateDescIdDesc(printerId)
                .stream().map(RelocationResponse::from).toList();
    }

    @Transactional
    public PrinterResponse relocate(long printerId, RelocationRequest request) {
        var printer = printers.findForUpdate(printerId)
                .orElseThrow(() -> new ApiException(NOT_FOUND, "Printer not found."));
        LocationService.checkVersion(request.version(), printer.getVersion());
        if (printer.getLocation().getId().equals(request.newLocationId())) {
            throw new ApiException(CONFLICT, "Choose a location different from the current location.");
        }
        if (request.relocationDate().isAfter(LocalDate.now(clock))) {
            throw new ApiException(BAD_REQUEST, "Relocation date cannot be in the future (" + clock.getZone() + ").");
        }
        history.findFirstByPrinterIdOrderByRelocationDateDescIdDesc(printerId).ifPresent(latest -> {
            if (request.relocationDate().isBefore(latest.getRelocationDate())) {
                throw new ApiException(CONFLICT, "Relocation date cannot be before the last recorded transfer: "
                        + latest.getRelocationDate() + ".");
            }
        });
        var destination = locations.findForUpdate(request.newLocationId());
        var previous = printer.getLocation();
        // Both writes commit together. A failure at either step rolls the transaction back.
        String remarks = InputText.optional(request.remarks());
        var relocation = history.saveAndFlush(new RelocationHistory(printer, previous, destination,
                request.relocationDate(), remarks, clock.instant()));
        printer.relocateTo(destination);
        printer = printers.saveAndFlush(printer);
        var oldValues = new java.util.LinkedHashMap<String, Object>();
        oldValues.put("location", AuditDetails.locationLabel(previous));
        var newValues = new java.util.LinkedHashMap<String, Object>();
        newValues.put("location", AuditDetails.locationLabel(destination));
        newValues.put("relocationDate", request.relocationDate());
        newValues.put("remarks", remarks);
        audit.record(AuditAction.RELOCATE, AuditEntityType.RELOCATION, relocation.getId(),
                AuditDetails.printerIdentifier(printer), "Printer " + printer.getSerialNumber() + " relocated from "
                        + AuditDetails.locationLabel(previous) + " to " + AuditDetails.locationLabel(destination) + ".",
                oldValues, newValues);
        return PrinterResponse.from(printer);
    }
}
