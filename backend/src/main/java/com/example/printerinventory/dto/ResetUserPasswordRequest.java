package com.example.printerinventory.dto;

import jakarta.validation.constraints.*;

public record ResetUserPasswordRequest(
        @NotBlank(message = "New Password is required") @Size(min = 12, max = 72, message = "Password must be 12 to 72 characters") String password,
        @NotBlank(message = "Confirm Password is required") String confirmPassword
) {}
