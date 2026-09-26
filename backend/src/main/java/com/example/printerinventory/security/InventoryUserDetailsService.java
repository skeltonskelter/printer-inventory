package com.example.printerinventory.security;

import com.example.printerinventory.repository.AppUserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class InventoryUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;

    public InventoryUserDetailsService(AppUserRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = username == null ? "" : username.trim();
        return users.findByUsernameIgnoreCase(normalized)
                .map(InventoryUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
