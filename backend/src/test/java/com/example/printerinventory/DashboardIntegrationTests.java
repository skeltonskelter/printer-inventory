package com.example.printerinventory;

import com.example.printerinventory.entity.PrinterStatus;
import java.util.ArrayList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Tag("integration")
class DashboardIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test
    void emptyDashboardHasAllZeroStatusesAndNoActivity() throws Exception {
        var result = dashboard();
        assertEquals(0, result.get("totalPrinters").asLong());
        assertEquals(0, result.get("relocatedPrinters").asLong());
        assertEquals(5, result.get("statusCounts").size());
        for (var status : PrinterStatus.values()) assertEquals(0, result.get("statusCounts").get(status.name()).asLong());
        assertEquals(0, result.get("recentlyAdded").size());
        assertEquals(0, result.get("recentTransfers").size());
        assertTrue(result.hasNonNull("checkedAt"));
    }

    @Test
    void statusCountsIncludeAllStatusesAndRelocationsCountDistinctLivePrinters() throws Exception {
        long a = location("ICT"), b = location("Accounting");
        long active = 0;
        for (var status : PrinterStatus.values()) {
            long id = printer(a, status.name(), status.ordinal());
            if (status == PrinterStatus.ACTIVE) active = id;
        }
        transfer(active, a, b, 1);
        transfer(active, b, a, 2);
        long deleted = printer(a, "ACTIVE", 8);
        transfer(deleted, a, b, 3);
        jdbc.update("update printers set deleted_at = now() where id = ?", deleted);
        var result = dashboard();
        assertEquals(5, result.get("totalPrinters").asLong());
        for (var status : PrinterStatus.values()) assertEquals(1, result.get("statusCounts").get(status.name()).asLong());
        assertEquals(1, result.get("relocatedPrinters").asLong());
        assertEquals(2, result.get("recentTransfers").size());
        jdbc.update("update printers set status = 'STORAGE' where id = ?", active);
        result = dashboard();
        assertEquals(0, result.get("statusCounts").get("ACTIVE").asLong());
        assertEquals(2, result.get("statusCounts").get("STORAGE").asLong());
        jdbc.update("update printers set deleted_at = now() where id = ?", active);
        result = dashboard();
        assertEquals(4, result.get("totalPrinters").asLong());
        assertEquals(0, result.get("relocatedPrinters").asLong());
        assertEquals(0, result.get("recentTransfers").size());
        assertEquals(3, jdbc.queryForObject("select count(*) from relocation_history", Integer.class));
    }

    @Test
    void recentListsAreBoundedSortedAndExcludeDeletedPrinters() throws Exception {
        long a = location("ICT"), b = location("Accounting");
        var ids = new ArrayList<Long>();
        for (int i = 0; i < 8; i++) {
            long id = printer(b, "ACTIVE", i);
            ids.add(id);
            transfer(id, a, b, i);
        }
        jdbc.update("update printers set deleted_at = now() where id = ?", ids.get(7));
        var result = dashboard();
        assertEquals(5, result.get("recentlyAdded").size());
        assertEquals(5, result.get("recentTransfers").size());
        for (int i = 0; i < 5; i++) {
            assertEquals(ids.get(6 - i).longValue(), result.get("recentlyAdded").get(i).get("id").asLong());
            var recent = result.get("recentTransfers").get(i);
            assertEquals(ids.get(6 - i).longValue(), recent.get("printer").get("id").asLong());
            assertEquals("ICT", recent.get("relocation").get("previousLocation").get("department").asText());
            assertEquals("Accounting", recent.get("relocation").get("newLocation").get("department").asText());
        }
    }

    private JsonNode dashboard() throws Exception {
        var response = mvc.perform(get("/api/dashboard")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }

    private long location(String department) {
        return jdbc.queryForObject("insert into locations (department) values (?) returning id", Long.class, department);
    }

    private long printer(long location, String status, int sequence) {
        return jdbc.queryForObject("""
                insert into printers (brand, model, serial_number, sticker_number, location_id, status, created_at, updated_at)
                values ('Epson', 'L5290', ?, ?, ?, ?, timestamptz '2026-01-01' + ? * interval '1 day', now()) returning id
                """, Long.class, "SERIAL-" + sequence, "DASH-" + sequence, location, status, sequence);
    }

    private void transfer(long printer, long from, long to, int sequence) {
        jdbc.update("""
                insert into relocation_history (printer_id, previous_location_id, new_location_id, relocation_date, created_at)
                values (?, ?, ?, date '2026-01-01', timestamptz '2026-02-01' + ? * interval '1 day')
                """, printer, from, to, sequence);
    }
}
