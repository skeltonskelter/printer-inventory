package com.example.printerinventory;

import com.example.printerinventory.controller.HealthController;
import com.example.printerinventory.service.HealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HealthEndpointTests {
    private JdbcTemplate jdbcTemplate;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new HealthController(new HealthService(jdbcTemplate))).build();
    }

    @Test
    void returnsOkWhenDatabaseQuerySucceeds() throws Exception {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.api").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.checkedAt").isNotEmpty());
        verify(jdbcTemplate).queryForObject("SELECT 1", Integer.class);
    }

    @Test
    void returnsUnavailableWithoutLeakingDatabaseErrors() throws Exception {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("private database details"));

        mvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.api").value("UP"))
                .andExpect(jsonPath("$.database").value("DOWN"))
                .andExpect(jsonPath("$.message").value(
                        "The backend is reachable, but PostgreSQL is unavailable."));
    }

    @Test
    void doesNotReportHealthyForAnUnexpectedQueryResult() throws Exception {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(null);
        mvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.database").value("DOWN"));
    }
}
