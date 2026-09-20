package com.example.printerinventory.dto;

import com.example.printerinventory.entity.RelocationHistory;
import java.time.Instant;
import java.time.LocalDate;

public record RelocationResponse(Long id, Long printerId, LocationResponse previousLocation,
                                  LocationResponse newLocation, LocalDate relocationDate,
                                  String remarks, Instant createdAt) {
    public static RelocationResponse from(RelocationHistory history) {
        return new RelocationResponse(history.getId(), history.getPrinter().getId(),
                LocationResponse.from(history.getPreviousLocation()), LocationResponse.from(history.getNewLocation()),
                history.getRelocationDate(), history.getRemarks(), history.getCreatedAt());
    }
}
