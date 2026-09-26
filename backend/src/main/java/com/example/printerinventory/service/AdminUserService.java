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
    private final AuditService audit;

    public AdminUserService(AppUserRepository users, PasswordEncoder passwords, AuditService audit) {
        this.users = users;
        this.passwords = passwords;
        this.audit = audit;
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
        user = users.saveAndFlush(user);
        audit.record(AuditAction.USER_CREATE, AuditEntityType.USER, user.getId(), user.getUsername(),
                "User " + user.getUsername() + " created.", null, AuditDetails.user(user));
        return AdminUserResponse.from(user);
    }

    @Transactional
    public AdminUserResponse update(long id, UpdateUserRequest request) {
        AppUser user = find(id);
        String username = clean(request.username());
        if (users.existsByUsernameIgnoreCaseAndIdNot(username, id)) duplicateUsername();
        ensureActiveAdminRemains(user, request.role(), request.enabled());
        var before = AuditDetails.user(user);
        user.updateProfile(username, clean(request.fullName()), request.role(), request.enabled());
        user = users.saveAndFlush(user);
        var after = AuditDetails.user(user);
        var changes = AuditDetails.changes(before, after);
        var profileOld = new java.util.LinkedHashMap<>(changes.oldValues());
        var profileNew = new java.util.LinkedHashMap<>(changes.newValues());
        Object oldRole = profileOld.remove("role"); Object newRole = profileNew.remove("role");
        Object oldEnabled = profileOld.remove("enabled"); Object newEnabled = profileNew.remove("enabled");
        if (!profileOld.isEmpty()) audit.record(AuditAction.USER_UPDATE, AuditEntityType.USER, user.getId(), user.getUsername(),
                "User " + user.getUsername() + " updated.", profileOld, profileNew);
        if (oldRole != null) audit.record(AuditAction.ROLE_CHANGE, AuditEntityType.USER, user.getId(), user.getUsername(),
                "Role changed for user " + user.getUsername() + ".", java.util.Map.of("role", oldRole), java.util.Map.of("role", newRole));
        if (oldEnabled != null) audit.record(user.isEnabled() ? AuditAction.USER_ENABLE : AuditAction.USER_DISABLE,
                AuditEntityType.USER, user.getId(), user.getUsername(),
                "User " + user.getUsername() + (user.isEnabled() ? " enabled." : " disabled."),
                java.util.Map.of("enabled", oldEnabled), java.util.Map.of("enabled", newEnabled));
        if (changes.oldValues().isEmpty()) audit.record(AuditAction.USER_UPDATE, AuditEntityType.USER, user.getId(), user.getUsername(),
                "User " + user.getUsername() + " updated.", null, null);
        return AdminUserResponse.from(user);
    }

    @Transactional
    public AdminUserResponse setStatus(long id, UserStatusRequest request) {
        AppUser user = find(id);
        ensureActiveAdminRemains(user, user.getRole(), request.enabled());
        boolean before = user.isEnabled();
        user.setEnabled(request.enabled());
        user = users.saveAndFlush(user);
        audit.record(user.isEnabled() ? AuditAction.USER_ENABLE : AuditAction.USER_DISABLE,
                AuditEntityType.USER, user.getId(), user.getUsername(),
                "User " + user.getUsername() + (user.isEnabled() ? " enabled." : " disabled."),
                java.util.Map.of("enabled", before), java.util.Map.of("enabled", user.isEnabled()));
        return AdminUserResponse.from(user);
    }

    @Transactional
    public void resetPassword(long id, ResetUserPasswordRequest request) {
        verifyPasswords(request.password(), request.confirmPassword());
        AppUser user = find(id);
        user.changePassword(passwords.encode(request.password()));
        users.saveAndFlush(user);
        audit.record(AuditAction.PASSWORD_RESET, AuditEntityType.USER, user.getId(), user.getUsername(),
                "Password reset for user " + user.getUsername() + ".", null, null);
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
