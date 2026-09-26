package com.example.printerinventory.security;

import com.example.printerinventory.entity.AppUser;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class InventoryUserPrincipal implements UserDetails {
    private final Long id;
    private final String username;
    private final String passwordHash;
    private final String fullName;
    private final String role;
    private final boolean enabled;

    public InventoryUserPrincipal(AppUser user) {
        id = user.getId();
        username = user.getUsername();
        passwordHash = user.getPasswordHash();
        fullName = user.getFullName();
        role = user.getRole().name();
        enabled = user.isEnabled();
    }

    public Long id() { return id; }
    public String fullName() { return fullName; }
    public String role() { return role; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isEnabled() { return enabled; }
}
