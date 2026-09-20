package com.example.printerinventory.dto;

import jakarta.validation.constraints.*;

public record LocationRequest(
        @NotBlank(message = "Department is required") @Size(max = 120) String department,
        @Size(max = 120) String building,
        @Size(max = 50) String floor,
        @Size(max = 80) String room,
        @Size(max = 1000) String description,
        @PositiveOrZero Long version
) {}
