package com.example.printerinventory.service;

import com.example.printerinventory.dto.*;
import com.example.printerinventory.entity.*;
import com.example.printerinventory.exception.ApiException;
import com.example.printerinventory.repository.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        if (printers.existsByStickerNumberIgnoreCaseAndIdNot(sticker, excludedId)) {
            throw new ApiException(CONFLICT, "Sticker number is already in use, including retained deleted printers.");
        }
        if (serial != null && printers.existsBySerialNumberIgnoreCaseAndIdNot(serial, excludedId)) {
            throw new ApiException(CONFLICT, "Serial number is already in use, including retained deleted printers.");
        }
        printer.update(request.brand().trim(), request.model().trim(), serial, sticker,
                request.status(), InputText.optional(request.remarks()));
    }
}
