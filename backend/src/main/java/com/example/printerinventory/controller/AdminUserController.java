package com.example.printerinventory.controller;

import com.example.printerinventory.dto.*;
import com.example.printerinventory.service.AdminUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService users;

    public AdminUserController(AdminUserService users) { this.users = users; }

    @GetMapping
    public List<AdminUserResponse> list() { return users.list(); }

    @PostMapping
    public ResponseEntity<AdminUserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        var user = users.create(request);
        return ResponseEntity.created(URI.create("/api/admin/users/" + user.id())).body(user);
    }

    @PutMapping("/{id}")
    public AdminUserResponse update(@PathVariable @Positive long id, @Valid @RequestBody UpdateUserRequest request) {
        return users.update(id, request);
    }

    @PutMapping("/{id}/status")
    public AdminUserResponse status(@PathVariable @Positive long id, @Valid @RequestBody UserStatusRequest request) {
        return users.setStatus(id, request);
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> password(@PathVariable @Positive long id,
                                         @Valid @RequestBody ResetUserPasswordRequest request) {
        users.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }
}
