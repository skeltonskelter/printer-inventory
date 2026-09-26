package com.example.printerinventory.service;

import com.example.printerinventory.dto.*;
import com.example.printerinventory.entity.PrinterStatus;
import com.example.printerinventory.repository.LocationRepository;
import com.example.printerinventory.repository.PrinterRepository;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PrinterImportService {
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> REQUIRED_HEADERS = Set.of("Serial Number", "Brand", "Model", "Status", "Department");
    private final PrinterRepository printers;
    private final LocationRepository locations;
    private final LocationService locationService;
    private final PrinterService printerService;
    private final AuditService audit;

    public PrinterImportService(PrinterRepository printers, LocationRepository locations,
                                LocationService locationService, PrinterService printerService, AuditService audit) {
        this.printers = printers;
        this.locations = locations;
        this.locationService = locationService;
        this.printerService = printerService;
        this.audit = audit;
    }

    public PrinterImportPreview preview(MultipartFile file) {
        var parsed = parse(file);
        return new PrinterImportPreview(parsed.totalRows(), parsed.rows().size(),
                parsed.errors().size(), parsed.errors());
    }

    @Transactional
    public PrinterImportResult confirm(MultipartFile file) {
        var parsed = parse(file);
        if (!parsed.errors().isEmpty()) return new PrinterImportResult(0, parsed.errors().size(), parsed.errors());
        var knownLocations = new HashMap<String, Long>();
        locations.findAll().forEach(location -> knownLocations.put(locationKey(location.getDepartment(), location.getSection(),
                location.getBuilding(), location.getFloor(), location.getRoom(), location.getDescription()), location.getId()));
        try {
            for (var row : parsed.rows()) {
                String key = locationKey(row.department(), row.section(), row.building(), row.floor(), row.room(), row.description());
                Long locationId = knownLocations.get(key);
                if (locationId == null) {
                    var created = locationService.createImported(new LocationRequest(row.department(), row.section(), row.building(),
                            row.floor(), row.room(), row.description(), null));
                    locationId = created.id();
                    knownLocations.put(key, locationId);
                }
                printerService.createImported(new PrinterRequest(row.brand(), row.model(), row.supplier(), parseDateOfPurchase(row.dateOfPurchase()), row.serialNumber(), row.stickerNumber(),
                        locationId, parseStatus(row.statusText()), row.remarks(), null));
            }
            var details = new LinkedHashMap<String, Object>();
            details.put("totalRows", parsed.totalRows()); details.put("importedRows", parsed.rows().size());
            details.put("invalidRows", 0);
            audit.record(com.example.printerinventory.entity.AuditAction.IMPORT,
                    com.example.printerinventory.entity.AuditEntityType.CSV_IMPORT, null,
                    "CSV Import", "CSV import completed.", null, details);
            return new PrinterImportResult(parsed.rows().size(), 0, List.of());
        } catch (RuntimeException exception) {
            throw new com.example.printerinventory.exception.ApiException(HttpStatus.CONFLICT,
                    "The import could not be completed because inventory data changed. No printers were imported.");
        }
    }

    private Parsed parse(MultipartFile file) {
        if (file == null || file.isEmpty()) return error(0, "Choose a non-empty CSV file.");
        if (file.getSize() > MAX_FILE_SIZE) return error(0, "CSV files must be 5 MB or smaller.");
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (!name.endsWith(".csv")) return error(0, "Choose a CSV file.");
        try (var reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             var csv = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).setTrim(false).build().parse(reader)) {
            var headers = new LinkedHashMap<String, String>();
            for (String raw : csv.getHeaderNames()) {
                String header = raw.replaceFirst("^\\uFEFF", "").trim();
                if (headers.putIfAbsent(header, raw) != null) return error(0, "The CSV contains a duplicate header: " + header + ".");
            }
            for (String required : REQUIRED_HEADERS) if (!headers.containsKey(required)) return error(0, "Missing required column: " + required + ".");
            var rows = new ArrayList<Row>();
            var errors = new ArrayList<ImportRowError>();
            var serials = new HashSet<String>();
            var stickers = new HashSet<String>();
            long totalRows = 0;
            for (CSVRecord record : csv) {
                totalRows++;
                long rowNumber = record.getRecordNumber() + 1;
                Row row = new Row(value(record, headers, "Serial Number"), value(record, headers, "Sticker Number"),
                        value(record, headers, "Brand"), value(record, headers, "Model"), value(record, headers, "Supplier"),
                        value(record, headers, "Date of Purchase"), value(record, headers, "Status"),
                        value(record, headers, "Department"), value(record, headers, "Section"), value(record, headers, "Building"),
                        value(record, headers, "Floor"), value(record, headers, "Room"), value(record, headers, "Location Description"),
                        value(record, headers, "Remarks"));
                String problem = validate(row, serials, stickers);
                if (problem == null) rows.add(row); else errors.add(new ImportRowError(rowNumber, problem));
            }
            if (rows.isEmpty() && errors.isEmpty()) return error(0, "The CSV contains a header but no data rows.");
            return new Parsed(totalRows, rows, errors);
        } catch (IOException | IllegalArgumentException exception) {
            return error(0, "The CSV file is malformed or cannot be read.");
        }
    }

    private String validate(Row row, Set<String> serials, Set<String> stickers) {
        if (row.serialNumber() == null) return "Serial Number is required.";
        if (row.brand() == null) return "Brand is required.";
        if (row.model() == null) return "Model is required.";
        if (row.department() == null) return "Department is required.";
        if (row.statusText() == null) return "Status is required.";
        if (row.serialNumber().length() > 120 || row.brand().length() > 100 || row.model().length() > 120) return "A required field exceeds its maximum length.";
        if (row.department().length() > 120 || tooLong(row.section(), 120) || tooLong(row.building(), 120) || tooLong(row.floor(), 50)
                || tooLong(row.room(), 80) || tooLong(row.description(), 1000) || tooLong(row.remarks(), 2000)
                || tooLong(row.stickerNumber(), 80) || tooLong(row.supplier(), 200)) return "A location or optional field exceeds its maximum length.";
        try { parseStatus(row.statusText()); }
        catch (IllegalArgumentException exception) { return "Invalid status: " + row.statusText() + "."; }
        try { parseDateOfPurchase(row.dateOfPurchase()); }
        catch (IllegalArgumentException exception) { return "Invalid Date of Purchase."; }
        String serialKey = row.serialNumber().toLowerCase(Locale.ROOT);
        if (!serials.add(serialKey)) return "Serial Number is duplicated in this CSV.";
        if (printers.existsBySerialNumberIgnoreCaseAndIdNot(row.serialNumber(), 0)) return "Serial Number already exists.";
        if (row.stickerNumber() != null) {
            String stickerKey = row.stickerNumber().toLowerCase(Locale.ROOT);
            if (!stickers.add(stickerKey)) return "Sticker Number is duplicated in this CSV.";
            if (printers.existsByStickerNumberIgnoreCaseAndIdNot(row.stickerNumber(), 0)) return "Sticker Number already exists.";
        }
        return null;
    }

    private static boolean tooLong(String value, int max) { return value != null && value.length() > max; }
    private static PrinterStatus parseStatus(String value) {
        return PrinterStatus.valueOf(value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "_"));
    }
    private static LocalDate parseDateOfPurchase(String value) {
        return value == null ? null : LocalDate.parse(value);
    }
    private static String value(CSVRecord record, Map<String, String> headers, String header) {
        String actual = headers.get(header);
        return actual == null ? null : InputText.optional(record.get(actual));
    }
    private static String locationKey(String department, String section, String building, String floor, String room, String description) {
        return String.join("\u0001", normalized(department), normalized(section), normalized(building), normalized(floor), normalized(room), normalized(description));
    }
    private static String normalized(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static Parsed error(long row, String message) { return new Parsed(0, List.of(), List.of(new ImportRowError(row, message))); }
    private record Parsed(long totalRows, List<Row> rows, List<ImportRowError> errors) {}
    private record Row(String serialNumber, String stickerNumber, String brand, String model, String supplier,
                       String dateOfPurchase, String statusText,
                       String department, String section, String building, String floor, String room,
                       String description, String remarks) {}
}
