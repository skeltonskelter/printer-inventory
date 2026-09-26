package com.example.printerinventory.dto;

import com.example.printerinventory.entity.UserRole;
import jakarta.validation.constraints.*;

public record CreateUserRequest(
        @NotBlank(message = "Full Name is required") @Size(max = 200) String fullName,
        @NotBlank(message = "Username is required") @Size(max = 100) String username,
        @NotBlank(message = "Password is required") @Size(min = 12, max = 72, message = "Password must be 12 to 72 characters") String password,
        @NotBlank(message = "Confirm Password is required") String confirmPassword,
        @NotNull(message = "Role is required") UserRole role,
        @NotNull(message = "Status is required") Boolean enabled
) {}
