package com.example.printerinventory;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** No test transaction: exercises real commits, rollback, and concurrent requests. */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class RelocationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    private final List<Long> printerIds = new ArrayList<>();
    private final List<Long> locationIds = new ArrayList<>();

    @BeforeEach
    void requireTestDatabase() {
        assertEquals("inventory_test", jdbc.queryForObject("select current_database()", String.class),
                "Run this suite only against the dedicated inventory_test database.");
    }

    @AfterEach
    void cleanOwnFixtures() {
        // Only IDs created by this test are removed; the app's soft-delete behavior is tested below.
        if (printerIds.isEmpty() && locationIds.isEmpty()) return;
        jdbc.execute("DROP TRIGGER IF EXISTS phase4_force_failure ON printers");
        jdbc.execute("DROP FUNCTION IF EXISTS phase4_force_failure()");
        for (long id : printerIds) {
            jdbc.update("delete from relocation_history where printer_id = ?", id);
            jdbc.update("delete from printers where id = ?", id);
        }
        for (long id : locationIds) jdbc.update("delete from locations where id = ?", id);
    }

    @Test
    void chainedTransfersHaveCorrectPreviousLocationsAndStableDates() throws Exception {
        long a = location("ICT"), b = location("Accounting"), c = location("Storage");
        JsonNode printer = printer(a);
        long id = printer.get("id").asLong();
        String createdAt = current(id).get("createdAt").asText();
        JsonNode moved = move(id, b, today().minusDays(1), 0, 200);
        assertEquals(b, moved.get("location").get("id").asLong());
        assertEquals(1, moved.get("version").asLong());
        assertEquals(createdAt, moved.get("createdAt").asText());
        assertEquals("ACTIVE", moved.get("status").asText());
        assertEquals("Printer remarks", moved.get("remarks").asText());
        moved = move(id, c, today(), 1, 200);
        assertEquals(c, moved.get("location").get("id").asLong());
        JsonNode records = history(id);
        assertEquals(2, records.size());
        assertEquals(b, records.get(0).get("previousLocation").get("id").asLong());
        assertEquals(c, records.get(0).get("newLocation").get("id").asLong());
        assertEquals(a, records.get(1).get("previousLocation").get("id").asLong());
        assertEquals(b, records.get(1).get("newLocation").get("id").asLong());
        assertEquals(today().toString(), records.get(0).get("relocationDate").asText());
        assertEquals("Department request", records.get(0).get("remarks").asText());
        assertTrue(records.get(0).hasNonNull("createdAt"));
    }

    @Test
    void sameDayReturnTransferIsAllowedAndNewestAppearsFirst() throws Exception {
        long a = location("ICT"), b = location("Accounting");
        long id = printer(a).get("id").asLong();
        move(id, b, today(), 0, 200);
        move(id, a, today(), 1, 200);
        assertEquals(a, history(id).get(0).get("newLocation").get("id").asLong());
        assertEquals(2, history(id).size());
    }

    @Test
    void invalidDestinationsDatesAndVersionsDoNotChangeThePrinter() throws Exception {
        long a = location("ICT"), b = location("Accounting");
        long id = printer(a).get("id").asLong();
        move(id, a, today(), 0, 409);
        move(id, Long.MAX_VALUE, today(), 0, 404);
        move(id, b, today().plusDays(1), 0, 400);
        move(id, b, today(), 8, 409);
        assertEquals(a, current(id).get("location").get("id").asLong());
        assertEquals(0, history(id).size());
        move(id, b, today(), 0, 200);
        move(id, a, today().minusDays(1), 1, 409);
        assertEquals(1, history(id).size());
        assertEquals(b, current(id).get("location").get("id").asLong());
    }

    @Test
    void missingFieldsMalformedDatesAndMissingPrintersAreRejected() throws Exception {
        long id = printer(location("ICT")).get("id").asLong();
        mvc.perform(post("/api/printers/{id}/relocate", id).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.newLocationId").exists())
                .andExpect(jsonPath("$.fieldErrors.relocationDate").exists()).andExpect(jsonPath("$.fieldErrors.version").exists());
        mvc.perform(post("/api/printers/{id}/relocate", id).contentType(APPLICATION_JSON)
                        .content("{\"newLocationId\":1,\"relocationDate\":\"not-a-date\",\"version\":0}"))
                .andExpect(status().isBadRequest());
        move(Long.MAX_VALUE, 1, today(), 0, 404);
        mvc.perform(get("/api/printers/9223372036854775807/relocations")).andExpect(status().isNotFound());
        assertEquals(0, history(id).size());
    }

    @Test
    void historicalLocationsAndDeletedPrinterHistoryArePreserved() throws Exception {
        long a = location("ICT"), b = location("Accounting");
        long id = printer(a).get("id").asLong();
        move(id, b, today(), 0, 200);
        mvc.perform(put("/api/locations/{id}", a).contentType(APPLICATION_JSON)
                        .content("{\"department\":\"Renamed\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.department").value("Renamed"));
        mvc.perform(delete("/api/locations/{id}", a)).andExpect(status().isConflict());
        mvc.perform(delete("/api/printers/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/printers/{id}", id)).andExpect(status().isNotFound());
        JsonNode retained = history(id);
        assertEquals(1, retained.size());
        assertEquals("Renamed", retained.get(0).get("previousLocation").get("department").asText());
        move(id, a, today(), 2, 404);
        mvc.perform(delete("/api/locations/{id}", b)).andExpect(status().isConflict());
    }

    @Test
    void databaseFailureAfterHistoryInsertRollsBackBothWrites() throws Exception {
        long a = location("ICT"), b = location("Accounting");
        long id = printer(a).get("id").asLong();
        // Deliberately reject the second write, after history.saveAndFlush has inserted a row.
        jdbc.execute("""
                CREATE FUNCTION phase4_force_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.id = %d AND NEW.location_id <> OLD.location_id THEN
                    RAISE EXCEPTION 'Test-only failure after history insert' USING ERRCODE = '23514';
                  END IF;
                  RETURN NEW;
                END $$
                """.formatted(id));
        jdbc.execute("CREATE TRIGGER phase4_force_failure BEFORE UPDATE ON printers FOR EACH ROW EXECUTE FUNCTION phase4_force_failure()");
        move(id, b, today(), 0, 409);
        assertEquals(a, current(id).get("location").get("id").asLong());
        assertEquals(0, current(id).get("version").asLong());
        assertEquals(0, history(id).size());
        jdbc.execute("DROP TRIGGER phase4_force_failure ON printers");
        jdbc.execute("DROP FUNCTION phase4_force_failure()");
        move(id, b, today(), 0, 200);
        assertEquals(1, history(id).size());
    }

    @Test
    void competingRequestsProduceOnlyOneTransferAndReplaysAreRejected() throws Exception {
        long a = location("ICT"), b = location("Accounting"), c = location("Storage");
        long id = printer(a).get("id").asLong();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return moveStatus(id, b); });
            var second = executor.submit(() -> { start.await(); return moveStatus(id, c); });
            start.countDown();
            var codes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))
                    .stream().sorted().toList();
            assertEquals(List.of(200, 409), codes);
        }
        JsonNode records = history(id);
        assertEquals(1, records.size());
        assertEquals(a, records.get(0).get("previousLocation").get("id").asLong());
        assertEquals(current(id).get("location").get("id").asLong(), records.get(0).get("newLocation").get("id").asLong());
        move(id, b, today(), 0, 409);
        assertEquals(1, history(id).size());
    }

    private int moveStatus(long id, long destination) throws Exception {
        return mvc.perform(post("/api/printers/{id}/relocate", id).contentType(APPLICATION_JSON)
                .content(body(destination, today(), 0))).andReturn().getResponse().getStatus();
    }

    private JsonNode move(long id, long destination, LocalDate date, long version, int expected) throws Exception {
        var result = mvc.perform(post("/api/printers/{id}/relocate", id).contentType(APPLICATION_JSON)
                        .content(body(destination, date, version)))
                .andExpect(status().is(expected)).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String body(long destination, LocalDate date, long version) {
        return json.writeValueAsString(Map.of("newLocationId", destination, "relocationDate", date.toString(),
                "version", version, "remarks", " Department request "));
    }

    private LocalDate today() { return LocalDate.now(clock); }

    private JsonNode current(long id) throws Exception {
        return json.readTree(mvc.perform(get("/api/printers/{id}", id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode history(long id) throws Exception {
        return json.readTree(mvc.perform(get("/api/printers/{id}/relocations", id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private long location(String department) throws Exception {
        JsonNode value = json.readTree(mvc.perform(post("/api/locations").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("department", department))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long id = value.get("id").asLong(); locationIds.add(id); return id;
    }

    private JsonNode printer(long location) throws Exception {
        JsonNode value = json.readTree(mvc.perform(post("/api/printers").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("brand", "Epson", "model", "L5290", "stickerNumber", UUID.randomUUID().toString(),
                                "locationId", location, "status", "ACTIVE", "remarks", "Printer remarks"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        printerIds.add(value.get("id").asLong()); return value;
    }
}
