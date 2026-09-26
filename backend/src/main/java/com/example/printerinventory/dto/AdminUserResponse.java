package com.example.printerinventory.dto;

import com.example.printerinventory.entity.AppUser;
import com.example.printerinventory.entity.UserRole;
import java.time.Instant;

public record AdminUserResponse(long id, String fullName, String username, UserRole role,
                                boolean enabled, Instant createdAt, Instant updatedAt) {
    public static AdminUserResponse from(AppUser user) {
        return new AdminUserResponse(user.getId(), user.getFullName(), user.getUsername(), user.getRole(),
                user.isEnabled(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
