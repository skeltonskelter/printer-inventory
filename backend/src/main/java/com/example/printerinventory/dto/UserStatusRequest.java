package com.example.printerinventory.dto;

import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(@NotNull(message = "Status is required") Boolean enabled) {}
