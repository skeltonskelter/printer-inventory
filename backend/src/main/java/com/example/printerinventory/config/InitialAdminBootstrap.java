package com.example.printerinventory.config;

import com.example.printerinventory.entity.AppUser;
import com.example.printerinventory.entity.UserRole;
import com.example.printerinventory.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InitialAdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(InitialAdminBootstrap.class);
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final String username;
    private final String password;
    private final String fullName;

    public InitialAdminBootstrap(AppUserRepository users, PasswordEncoder passwords,
            @Value("${app.initial-admin.username:}") String username,
            @Value("${app.initial-admin.password:}") String password,
            @Value("${app.initial-admin.full-name:}") String fullName) {
        this.users = users;
        this.passwords = passwords;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean anyConfigured = !username.isBlank() || !password.isBlank() || !fullName.isBlank();
        if (!anyConfigured) return;
        if (username.isBlank() || password.isBlank() || fullName.isBlank()) {
            throw new IllegalStateException("All initial administrator environment variables must be supplied together.");
        }
        String cleanUsername = username.trim();
        String cleanFullName = fullName.trim();
        if (cleanUsername.length() > 100 || cleanFullName.length() > 200) {
            throw new IllegalStateException("Initial administrator username or full name is too long.");
        }
        if (password.length() < 12) {
            throw new IllegalStateException("Initial administrator password must contain at least 12 characters.");
        }
        if (users.existsByUsernameIgnoreCase(cleanUsername)) {
            log.info("Initial administrator already exists; bootstrap made no changes.");
            return;
        }
        users.saveAndFlush(new AppUser(cleanUsername, passwords.encode(password), cleanFullName,
                UserRole.ADMIN, true));
        log.info("Initial administrator account created.");
    }
}
