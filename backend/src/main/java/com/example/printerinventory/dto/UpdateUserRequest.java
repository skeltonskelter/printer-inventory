package com.example.printerinventory.dto;

import com.example.printerinventory.entity.UserRole;
import jakarta.validation.constraints.*;

public record UpdateUserRequest(
        @NotBlank(message = "Full Name is required") @Size(max = 200) String fullName,
        @NotBlank(message = "Username is required") @Size(max = 100) String username,
        @NotNull(message = "Role is required") UserRole role,
        @NotNull(message = "Status is required") Boolean enabled
) {}
