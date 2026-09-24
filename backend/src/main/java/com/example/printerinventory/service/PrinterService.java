package com.example.printerinventory.service;

import com.example.printerinventory.dto.*;
import com.example.printerinventory.entity.*;
import com.example.printerinventory.exception.ApiException;
import com.example.printerinventory.repository.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(readOnly = true)
public class PrinterService {
    private final PrinterRepository printers;
    private final LocationService locations;

    public PrinterService(PrinterRepository printers, LocationService locations) {
        this.printers = printers;
        this.locations = locations;
    }

    public PageResponse<PrinterResponse> list(String search, String brand, Long locationId,
                                             PrinterStatus status, int page, int size) {
        var results = printers.findAll(PrinterSpecifications.matching(search, brand, locationId, status),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(results.map(PrinterResponse::from));
    }

    public byte[] exportCsv(String search, String brand, Long locationId, PrinterStatus status) {
        var printersToExport = printers.findAll(
                PrinterSpecifications.matching(search, brand, locationId, status),
                Sort.by(Sort.Direction.DESC, "id"));
        var csv = new StringBuilder("Serial Number,Sticker Number,Brand,Model,Supplier,Date of Purchase,Status,Department,Section,Building,Floor,Room,Location Description,Remarks,Date Added,Last Updated\r\n");
        for (var printer : printersToExport) {
            var location = printer.getLocation();
            csv.append(row(
                    printer.getSerialNumber(), printer.getStickerNumber(), printer.getBrand(), printer.getModel(),
                    printer.getSupplier(), printer.getDateOfPurchase() == null ? null : printer.getDateOfPurchase().toString(),
                    printer.getStatus().name(), location.getDepartment(), location.getSection(), location.getBuilding(),
                    location.getFloor(), location.getRoom(), location.getDescription(), printer.getRemarks(),
                    DateTimeFormatter.ISO_INSTANT.format(printer.getCreatedAt()),
                    DateTimeFormatter.ISO_INSTANT.format(printer.getUpdatedAt())));
        }
        // BOM lets Excel recognize UTF-8 reliably while preserving all CSV data.
        return ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
    }

    private static String row(String... values) {
        var result = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) result.append(',');
            result.append(cell(values[i]));
        }
        return result.append("\r\n").toString();
    }

    private static String cell(String value) {
        if (value == null) value = "";
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) value = "'" + value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    public PrinterResponse get(long id) { return PrinterResponse.from(find(id)); }

    @Transactional
    public PrinterResponse create(PrinterRequest request) {
        Printer printer = new Printer();
        printer.assignInitialLocation(locations.findForUpdate(request.locationId()));
        apply(printer, request);
        return PrinterResponse.from(printers.saveAndFlush(printer));
    }

    @Transactional
    public PrinterResponse update(long id, PrinterRequest request) {
        Printer printer = find(id);
        LocationService.checkVersion(request.version(), printer.getVersion());
        if (!printer.getLocation().getId().equals(request.locationId())) {
            throw new ApiException(CONFLICT,
                    "Location changes require a relocation record. Use the relocate endpoint.");
        }
        apply(printer, request);
        return PrinterResponse.from(printers.saveAndFlush(printer));
    }

    @Transactional
    public void delete(long id) {
        Printer printer = find(id);
        printer.markDeleted();
        printers.flush();
    }

    private Printer find(long id) {
        return printers.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(NOT_FOUND, "Printer not found."));
    }

    private void apply(Printer printer, PrinterRequest request) {
        String sticker = InputText.identifier(request.stickerNumber());
        String serial = InputText.identifier(request.serialNumber());
        long excludedId = printer.getId() == null ? 0 : printer.getId();
        if (sticker != null && printers.existsByStickerNumberIgnoreCaseAndIdNot(sticker, excludedId)) {
            throw new ApiException(CONFLICT, "Sticker number is already in use, including retained deleted printers.");
        }
        if (serial != null && printers.existsBySerialNumberIgnoreCaseAndIdNot(serial, excludedId)) {
            throw new ApiException(CONFLICT, "Serial number is already in use, including retained deleted printers.");
        }
        printer.update(request.brand().trim(), request.model().trim(), InputText.optional(request.supplier()),
                request.dateOfPurchase(), serial, sticker,
                request.status(), InputText.optional(request.remarks()));
    }
}
