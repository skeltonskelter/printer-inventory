package com.example.printerinventory;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Full Spring + Flyway + JPA + PostgreSQL API tests. Every test rolls back its records. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Tag("integration")
class InventoryIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void healthChecksTheRealDatabase() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.database").value("UP"));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success", Integer.class));
    }

    @Test
    void locationCreateReadUpdateDelete() throws Exception {
        JsonNode location = createLocation(" ICT ");
        long id = location.get("id").asLong();
        assertEquals("ICT", location.get("department").asText());
        assertEquals("Operations", location.get("section").asText());
        mvc.perform(get("/api/locations/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.room").value("101"));
        mvc.perform(get("/api/locations")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].department").value("ICT"));
        mvc.perform(put("/api/locations/{id}", id).contentType(APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("department", "Accounting", "building", "Main", "version", 0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.department").value("Accounting"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(delete("/api/locations/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/locations/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void printerCreateReadAndEditPreserveItsCreationTimestamp() throws Exception {
        long location = createLocation("ICT").get("id").asLong();
        JsonNode created = createPrinter(location, " ict-prn-001 ", " sn-001 ", "ACTIVE");
        long id = created.get("id").asLong();
        assertEquals("ICT-PRN-001", created.get("stickerNumber").asText());
        assertEquals("SN-001", created.get("serialNumber").asText());
        assertEquals("ICT", created.get("location").get("department").asText());
        Map<String, Object> edit = printerBody(location, "ICT-PRN-001", "SN-001", "UNDER_REPAIR");
        edit.put("model", "L6270");
        edit.put("remarks", " Paper feed issue ");
        edit.put("version", created.get("version").asLong());
        mvc.perform(put("/api/printers/{id}", id).contentType(APPLICATION_JSON).content(json.writeValueAsString(edit)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.model").value("L6270"))
                .andExpect(jsonPath("$.status").value("UNDER_REPAIR"))
                .andExpect(jsonPath("$.remarks").value("Paper feed issue"))
                .andExpect(jsonPath("$.createdAt").value(created.get("createdAt").asText()))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/printers/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("L6270"));
        mvc.perform(put("/api/printers/{id}", id).contentType(APPLICATION_JSON).content(json.writeValueAsString(edit)))
                .andExpect(status().isConflict());
    }

    @Test
    void deletionRetainsPrinterAndReservesIdentifiers() throws Exception {
        long location = createLocation("ICT").get("id").asLong();
        long id = createPrinter(location, "ICT-PRN-001", "SN-001", "ACTIVE").get("id").asLong();
        mvc.perform(delete("/api/printers/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/printers/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/printers/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/printers").param("search", "ICT-PRN-001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertEquals(1, jdbc.queryForObject("select count(*) from printers where id = ? and deleted_at is not null",
                Integer.class, id));
        mvc.perform(post("/api/printers").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(printerBody(location, "ict-prn-001", "OTHER", "ACTIVE"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(
                        "Sticker number is already in use, including retained deleted printers."));
        mvc.perform(post("/api/printers").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(printerBody(location, "NEW-STICKER", "sn-001", "ACTIVE"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(
                        "Serial number is already in use, including retained deleted printers."));
        mvc.perform(delete("/api/locations/{id}", location)).andExpect(status().isConflict());
    }

    @Test
    void usedLocationCannotBeEditedOrDeletedAndPrinterCannotBeMovedSilently() throws Exception {
        long oldLocation = createLocation("ICT").get("id").asLong();
        long newLocation = createLocation("Accounting").get("id").asLong();
        long printer = createPrinter(oldLocation, "ICT-PRN-001", null, "ACTIVE").get("id").asLong();
        mvc.perform(put("/api/locations/{id}", oldLocation).contentType(APPLICATION_JSON)
                        .content("{\"department\":\"Changed\",\"version\":0}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/locations/{id}", oldLocation)).andExpect(status().isConflict());
        var edit = printerBody(newLocation, "ICT-PRN-001", null, "ACTIVE");
        edit.put("version", 0);
        mvc.perform(put("/api/printers/{id}", printer).contentType(APPLICATION_JSON).content(json.writeValueAsString(edit)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/printers/{id}", printer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.location.id").value(oldLocation));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ict-prn", "sn-001", "ePsOn", "l529"})
    void searchMatchesStickerSerialBrandAndModelIgnoringCase(String term) throws Exception {
        long location = createLocation("ICT").get("id").asLong();
        createPrinter(location, "ICT-PRN-001", "SN-001", "ACTIVE");
        mvc.perform(get("/api/printers/search").param("search", term))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void combinedFiltersPaginationAndLiteralWildcards() throws Exception {
        long firstLocation = createLocation("ICT").get("id").asLong();
        long secondLocation = createLocation("Accounting").get("id").asLong();
        createPrinter(firstLocation, "ICT-PRN-001", null, "ACTIVE");
        createPrinter(secondLocation, "ICT-PRN-002", null, "STORAGE");
        mvc.perform(get("/api/printers").param("brand", "epson").param("status", "STORAGE")
                        .param("locationId", Long.toString(secondLocation)).param("search", "L5290"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].stickerNumber").value("ICT-PRN-002"));
        mvc.perform(get("/api/printers").param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].stickerNumber").value("ICT-PRN-001"));
        mvc.perform(get("/api/printers").param("search", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/printers").param("search", "_"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/printers").param("search", "' OR 1=1 --"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "UNDER_REPAIR", "FOR_REPAIR", "STORAGE", "RETIRED", "DISPOSED"})
    void acceptsEachSupportedStatus(String printerStatus) throws Exception {
        long location = createLocation("ICT").get("id").asLong();
        assertEquals(printerStatus, createPrinter(location, "ICT-PRN-001", null, printerStatus).get("status").asText());
    }

    @Test
    void unknownSerialNumbersAreStoredAsNullAndCanRepeat() throws Exception {
        long location = createLocation("ICT").get("id").asLong();
        assertTrue(createPrinter(location, "ICT-PRN-001", "  ", "ACTIVE").get("serialNumber").isNull());
        assertTrue(createPrinter(location, "ICT-PRN-002", null, "ACTIVE").get("serialNumber").isNull());
    }

    @Test
    void invalidBodiesAndParametersReturnUnderstandableErrors() throws Exception {
        mvc.perform(post("/api/printers").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.brand").value("Brand is required"))
                .andExpect(jsonPath("$.fieldErrors.locationId").value("Location is required"))
                .andExpect(jsonPath("$.path").value("/api/printers"));
        mvc.perform(post("/api/locations").contentType(APPLICATION_JSON).content("{\"department\":\"  \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.department").exists());
        mvc.perform(post("/api/printers").contentType(APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/printers").contentType(APPLICATION_JSON).content("{\"status\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/printers").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/printers").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/printers").param("status", "INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/printers/not-a-number")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/locations/0")).andExpect(status().isBadRequest());
    }

    @Test
    void missingReferencesAre404AndInvalidCreationDoesNotWriteAPrinter() throws Exception {
        mvc.perform(get("/api/printers/9223372036854775807")).andExpect(status().isNotFound());
        mvc.perform(post("/api/printers").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(printerBody(Long.MAX_VALUE, "ICT-PRN-001", null, "ACTIVE"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Location not found."));
        assertEquals(0, jdbc.queryForObject("select count(*) from printers", Integer.class));
    }

    @Test
    void updateRequiresVersionAndLocationRejectsStaleVersion() throws Exception {
        long location = createLocation("ICT").get("id").asLong();
        mvc.perform(put("/api/locations/{id}", location).contentType(APPLICATION_JSON)
                        .content("{\"department\":\"Accounting\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/locations/{id}", location).contentType(APPLICATION_JSON)
                        .content("{\"department\":\"Accounting\",\"version\":9}"))
                .andExpect(status().isConflict());
        long printer = createPrinter(location, "ICT-PRN-001", null, "ACTIVE").get("id").asLong();
        mvc.perform(put("/api/printers/{id}", printer).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(printerBody(location, "ICT-PRN-001", null, "ACTIVE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void databaseEnforcesStickerUniquenessEvenOutsideTheService() throws Exception {
        long location = createLocation("ICT").get("id").asLong();
        createPrinter(location, "ICT-PRN-001", null, "ACTIVE");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into printers (brand, model, sticker_number, location_id, status, created_at, updated_at)
                values ('Epson', 'L5290', ' ict-prn-001 ', ?, 'ACTIVE', now(), now())
                """, location));
    }

    @Test
    void locationSectionIsOptionalAndMayBeShared() throws Exception {
        JsonNode first = json.readTree(mvc.perform(post("/api/locations").contentType(APPLICATION_JSON)
                        .content("{\"department\":\"Operations\",\"section\":\"Section A\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        JsonNode second = json.readTree(mvc.perform(post("/api/locations").contentType(APPLICATION_JSON)
                        .content("{\"department\":\"Finance\",\"section\":\"Section A\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertEquals("Section A", first.get("section").asText());
        assertEquals("Section A", second.get("section").asText());
        JsonNode optional = json.readTree(mvc.perform(post("/api/locations").contentType(APPLICATION_JSON)
                        .content("{\"department\":\"Unassigned\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertTrue(optional.get("section").isNull());
    }

    private JsonNode createLocation(String department) throws Exception {
        String body = json.writeValueAsString(Map.of("department", department, "section", "Operations",
                "building", "Main", "room", "101"));
        var result = mvc.perform(post("/api/locations").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode createPrinter(long location, String sticker, String serial, String printerStatus) throws Exception {
        var result = mvc.perform(post("/api/printers").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(printerBody(location, sticker, serial, printerStatus))))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private Map<String, Object> printerBody(long location, String sticker, String serial, String printerStatus) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("brand", " Epson ");
        body.put("model", "L5290");
        body.put("stickerNumber", sticker);
        body.put("serialNumber", serial);
        body.put("locationId", location);
        body.put("status", printerStatus);
        return body;
    }
}
