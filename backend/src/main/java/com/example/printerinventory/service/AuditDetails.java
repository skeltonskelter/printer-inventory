package com.example.printerinventory.service;

import com.example.printerinventory.entity.*;
import java.util.*;

public final class AuditDetails {
    private AuditDetails() {}

    public static String printerIdentifier(Printer printer) { return printer.getSerialNumber(); }

    public static Map<String, Object> printer(Printer value) {
        var result = new LinkedHashMap<String, Object>();
        result.put("serialNumber", value.getSerialNumber());
        result.put("stickerNumber", value.getStickerNumber());
        result.put("brand", value.getBrand());
        result.put("model", value.getModel());
        result.put("supplier", value.getSupplier());
        result.put("dateOfPurchase", value.getDateOfPurchase());
        result.put("location", locationLabel(value.getLocation()));
        result.put("status", value.getStatus());
        result.put("remarks", value.getRemarks());
        return result;
    }

    public static Map<String, Object> location(Location value) {
        var result = new LinkedHashMap<String, Object>();
        result.put("department", value.getDepartment()); result.put("section", value.getSection());
        result.put("building", value.getBuilding()); result.put("floor", value.getFloor());
        result.put("room", value.getRoom()); result.put("description", value.getDescription());
        return result;
    }

    public static Map<String, Object> user(AppUser value) {
        var result = new LinkedHashMap<String, Object>();
        result.put("fullName", value.getFullName()); result.put("username", value.getUsername());
        result.put("role", value.getRole()); result.put("enabled", value.isEnabled());
        return result;
    }

    public static Changes changes(Map<String, Object> before, Map<String, Object> after) {
        var oldValues = new LinkedHashMap<String, Object>();
        var newValues = new LinkedHashMap<String, Object>();
        after.forEach((key, value) -> {
            if (!Objects.equals(before.get(key), value)) {
                oldValues.put(key, before.get(key)); newValues.put(key, value);
            }
        });
        return new Changes(oldValues, newValues);
    }

    public static String locationLabel(Location location) {
        return String.join(" · ", java.util.stream.Stream.of(location.getDepartment(), location.getSection(),
                location.getBuilding(), location.getFloor() == null ? null : "Floor " + location.getFloor(),
                location.getRoom() == null ? null : "Room " + location.getRoom())
                .filter(value -> value != null && !value.isBlank()).toList());
    }

    public record Changes(Map<String, Object> oldValues, Map<String, Object> newValues) {}
}
