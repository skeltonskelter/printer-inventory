package com.example.printerinventory.service;

import com.example.printerinventory.dto.*;
import com.example.printerinventory.entity.*;
import com.example.printerinventory.exception.ApiException;
import com.example.printerinventory.repository.AppUserRepository;
import java.util.List;
import java.nio.charset.StandardCharsets;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(readOnly = true)
public class AdminUserService {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;

    public AdminUserService(AppUserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    public List<AdminUserResponse> list() {
        return users.findAll(Sort.by(Sort.Order.asc("fullName").ignoreCase(), Sort.Order.asc("id")))
                .stream().map(AdminUserResponse::from).toList();
    }

    @Transactional
    public AdminUserResponse create(CreateUserRequest request) {
        verifyPasswords(request.password(), request.confirmPassword());
        String username = clean(request.username());
        if (users.existsByUsernameIgnoreCase(username)) duplicateUsername();
        var user = new AppUser(username, passwords.encode(request.password()), clean(request.fullName()),
                request.role(), request.enabled());
        return AdminUserResponse.from(users.saveAndFlush(user));
    }

    @Transactional
    public AdminUserResponse update(long id, UpdateUserRequest request) {
        AppUser user = find(id);
        String username = clean(request.username());
        if (users.existsByUsernameIgnoreCaseAndIdNot(username, id)) duplicateUsername();
        ensureActiveAdminRemains(user, request.role(), request.enabled());
        user.updateProfile(username, clean(request.fullName()), request.role(), request.enabled());
        return AdminUserResponse.from(users.saveAndFlush(user));
    }

    @Transactional
    public AdminUserResponse setStatus(long id, UserStatusRequest request) {
        AppUser user = find(id);
        ensureActiveAdminRemains(user, user.getRole(), request.enabled());
        user.setEnabled(request.enabled());
        return AdminUserResponse.from(users.saveAndFlush(user));
    }

    @Transactional
    public void resetPassword(long id, ResetUserPasswordRequest request) {
        verifyPasswords(request.password(), request.confirmPassword());
        AppUser user = find(id);
        user.changePassword(passwords.encode(request.password()));
        users.saveAndFlush(user);
    }

    private AppUser find(long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(NOT_FOUND, "User not found."));
    }

    private void ensureActiveAdminRemains(AppUser user, UserRole newRole, boolean newEnabled) {
        if (user.isEnabled() && user.getRole() == UserRole.ADMIN
                && (!newEnabled || newRole != UserRole.ADMIN)
                && users.findActiveAdminsForUpdate().size() <= 1) {
            throw new ApiException(CONFLICT, "At least one active administrator must remain.");
        }
    }

    private static String clean(String value) { return value.trim(); }

    private static void verifyPasswords(String password, String confirmation) {
        if (!password.equals(confirmation)) {
            throw new ApiException(BAD_REQUEST, "Passwords do not match.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes.");
        }
    }

    private static void duplicateUsername() {
        throw new ApiException(CONFLICT, "Username already exists.");
    }
}
