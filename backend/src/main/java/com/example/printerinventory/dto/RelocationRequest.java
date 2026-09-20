package com.example.printerinventory.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record RelocationRequest(
        @NotNull(message = "New location is required") @Positive Long newLocationId,
        @NotNull(message = "Relocation date is required") LocalDate relocationDate,
        @Size(max = 2000) String remarks,
        @NotNull(message = "Version is required. Reload the printer and try again.") @PositiveOrZero Long version
) {}
