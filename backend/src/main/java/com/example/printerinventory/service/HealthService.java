package com.example.printerinventory.service;

import com.example.printerinventory.dto.HealthResponse;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private final JdbcTemplate jdbcTemplate;

    public HealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HealthResponse check() {
        try {
            // A real query verifies the configured credentials and database connection.
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (Integer.valueOf(1).equals(result)) {
                return new HealthResponse("UP", "UP", "UP",
                        "The backend and PostgreSQL are connected.", Instant.now());
            }
        } catch (DataAccessException exception) {
            // Never return database exception details or credentials to the browser.
        }
        return new HealthResponse("DOWN", "UP", "DOWN",
                "The backend is reachable, but PostgreSQL is unavailable.", Instant.now());
    }
}
