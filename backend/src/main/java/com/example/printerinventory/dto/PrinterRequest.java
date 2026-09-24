package com.example.printerinventory.dto;

import com.example.printerinventory.entity.PrinterStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record PrinterRequest(
        @NotBlank(message = "Brand is required") @Size(max = 100) String brand,
        @NotBlank(message = "Model is required") @Size(max = 120) String model,
        @Size(max = 200) String supplier,
        LocalDate dateOfPurchase,
        @NotBlank(message = "Serial number is required") @Size(max = 120) String serialNumber,
        @Size(max = 80) String stickerNumber,
        @NotNull(message = "Location is required") @Positive Long locationId,
        @NotNull(message = "Status is required") PrinterStatus status,
        @Size(max = 2000) String remarks,
        @PositiveOrZero Long version
) {}
