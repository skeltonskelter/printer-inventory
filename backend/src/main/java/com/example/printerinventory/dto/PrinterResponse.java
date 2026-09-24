package com.example.printerinventory.dto;

import com.example.printerinventory.entity.Printer;
import com.example.printerinventory.entity.PrinterStatus;
import java.time.Instant;

public record PrinterResponse(Long id, String brand, String model, String serialNumber,
                              String supplier, java.time.LocalDate dateOfPurchase, String stickerNumber,
                              LocationResponse location, PrinterStatus status,
                              String remarks, Instant createdAt, Instant updatedAt, long version) {
    public static PrinterResponse from(Printer printer) {
        return new PrinterResponse(printer.getId(), printer.getBrand(), printer.getModel(),
                printer.getSerialNumber(), printer.getSupplier(), printer.getDateOfPurchase(), printer.getStickerNumber(),
                LocationResponse.from(printer.getLocation()),
                printer.getStatus(), printer.getRemarks(), printer.getCreatedAt(), printer.getUpdatedAt(),
                printer.getVersion());
    }
}
