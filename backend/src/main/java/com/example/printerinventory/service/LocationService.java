package com.example.printerinventory.service;

import com.example.printerinventory.dto.*;
import com.example.printerinventory.entity.*;
import com.example.printerinventory.exception.ApiException;
import com.example.printerinventory.repository.*;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(readOnly = true)
public class LocationService {
    private final LocationRepository locations;
    private final PrinterRepository printers;
    private final RelocationHistoryRepository history;
    private final AuditService audit;

    public LocationService(LocationRepository locations, PrinterRepository printers, RelocationHistoryRepository history,
                           AuditService audit) {
        this.locations = locations;
        this.printers = printers;
        this.history = history;
        this.audit = audit;
    }

    public List<LocationResponse> list() {
        return locations.findAll(Sort.by("department", "section", "building", "floor", "room", "id"))
                .stream().map(LocationResponse::from).toList();
    }

    public LocationResponse get(long id) {
        return LocationResponse.from(locations.findById(id)
                .orElseThrow(() -> new ApiException(NOT_FOUND, "Location not found.")));
    }

    @Transactional
    public LocationResponse create(LocationRequest request) {
        return create(request, true);
    }

    LocationResponse createImported(LocationRequest request) { return create(request, false); }

    private LocationResponse create(LocationRequest request, boolean recordAudit) {
        Location location = new Location();
        apply(location, request);
        location = locations.saveAndFlush(location);
        if (recordAudit) audit.record(AuditAction.CREATE, AuditEntityType.LOCATION, location.getId(),
                AuditDetails.locationLabel(location), "Location created.", null, AuditDetails.location(location));
        return LocationResponse.from(location);
    }

    @Transactional
    public LocationResponse update(long id, LocationRequest request) {
        Location location = findForUpdate(id);
        checkVersion(request.version(), location.getVersion());
        var before = AuditDetails.location(location);
        apply(location, request);
        location = locations.saveAndFlush(location);
        var changes = AuditDetails.changes(before, AuditDetails.location(location));
        audit.record(AuditAction.UPDATE, AuditEntityType.LOCATION, location.getId(),
                AuditDetails.locationLabel(location), "Location updated.", changes.oldValues(), changes.newValues());
        return LocationResponse.from(location);
    }

    @Transactional
    public void delete(long id) {
        Location location = findForUpdate(id);
        ensureUnused(id);
        var values = AuditDetails.location(location);
        String identifier = AuditDetails.locationLabel(location);
        locations.delete(location);
        locations.flush();
        audit.record(AuditAction.DELETE, AuditEntityType.LOCATION, id, identifier,
                "Location deleted.", values, null);
    }

    @Transactional
    public Location findForUpdate(long id) {
        return locations.findForUpdate(id)
                .orElseThrow(() -> new ApiException(NOT_FOUND, "Location not found."));
    }

    private void ensureUnused(long id) {
        if (printers.existsByLocationId(id) || history.existsByPreviousLocationIdOrNewLocationId(id, id)) {
            throw new ApiException(CONFLICT,
                    "This location is referenced by a printer or relocation history and cannot be changed or deleted.");
        }
    }

    static void checkVersion(Long requested, long current) {
        if (requested == null) throw new ApiException(BAD_REQUEST, "Version is required when updating. Use the version returned by GET.");
        if (requested != current) throw new ApiException(CONFLICT, "This record has changed. Reload it before updating.");
    }

    private void apply(Location location, LocationRequest request) {
        location.update(request.department().trim(), InputText.optional(request.section()),
                InputText.optional(request.building()),
                InputText.optional(request.floor()), InputText.optional(request.room()),
                InputText.optional(request.description()));
    }
}
