package com.example.printerinventory.dto;

import com.example.printerinventory.entity.Location;

public record LocationResponse(Long id, String department, String section, String building, String floor,
                               String room, String description, long version) {
    public static LocationResponse from(Location location) {
        return new LocationResponse(location.getId(), location.getDepartment(), location.getSection(),
                location.getBuilding(), location.getFloor(), location.getRoom(), location.getDescription(),
                location.getVersion());
    }
}
