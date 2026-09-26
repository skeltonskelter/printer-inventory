package com.example.printerinventory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Full Spring + Flyway + JPA + PostgreSQL API tests. Every test rolls back its records. */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
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
    void optionalSupplierAndPurchaseDateCanBeCreatedEditedAndCleared() throws Exception {
        long location = createLocation("ICT").get("id").asLong();
        var body = printerBody(location, "SUPPLIER-1", "SN-SUPPLIER-1", "ACTIVE");
        body.put("supplier", "Epson Authorized Dealer");
        body.put("dateOfPurchase", "2026-09-15");
        JsonNode created = json.readTree(mvc.perform(post("/api/printers").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(body))).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString());
        assertEquals("Epson Authorized Dealer", created.get("supplier").asText());
        assertEquals("2026-09-15", created.get("dateOfPurchase").asText());
        body.put("supplier", "");
        body.put("dateOfPurchase", null);
        body.put("version", created.get("version").asLong());
        mvc.perform(put("/api/printers/{id}", created.get("id").asLong()).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(body))).andExpect(status().isOk())
                .andExpect(jsonPath("$.supplier").value(nullValue()))
                .andExpect(jsonPath("$.dateOfPurchase").value(nullValue()));
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
    void usedLocationCanBeEditedWithoutChangingPrinterAssignmentAndCannotBeDeleted() throws Exception {
        long oldLocation = createLocation("ICT").get("id").asLong();
        long newLocation = createLocation("Accounting").get("id").asLong();
        long printer = createPrinter(oldLocation, "ICT-PRN-001", "SN-USED", "ACTIVE").get("id").asLong();
        mvc.perform(put("/api/locations/{id}", oldLocation).contentType(APPLICATION_JSON)
                        .content("{\"department\":\"Changed\",\"section\":\"Updated\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.department").value("Changed"))
                .andExpect(jsonPath("$.section").value("Updated"));
        mvc.perform(get("/api/printers/{id}", printer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.location.id").value(oldLocation))
                .andExpect(jsonPath("$.location.department").value("Changed"));
        mvc.perform(delete("/api/locations/{id}", oldLocation)).andExpect(status().isConflict());
        var edit = printerBody(newLocation, "ICT-PRN-001", "SN-USED", "ACTIVE");
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
        createPrinter(firstLocation, "ICT-PRN-001", "SN-001", "ACTIVE");
        createPrinter(secondLocation, "ICT-PRN-002", "SN-002", "STORAGE");
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

    @Test
    void csvExportIncludesAllFilteredRowsAndEscapesOptionalText() throws Exception {
        long location = createLocation("ICT").get("id").asLong();
        long first = createPrinter(location, "ICT-PRN-CSV", "SN-CSV-1", "ACTIVE").get("id").asLong();
        createPrinter(location, null, "SN-CSV-2", "RETIRED");
        mvc.perform(put("/api/printers/{id}", first).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "brand", "Epson", "model", "L5290", "supplier", "Office, \"Supply\" Co.",
                                "dateOfPurchase", "2026-09-15", "stickerNumber", "ICT-PRN-CSV",
                                "serialNumber", "SN-CSV-1", "locationId", location, "status", "ACTIVE",
                                "remarks", "Comma, \"quote\"\nnext", "version", 0))))
                .andExpect(status().isOk());
        var response = mvc.perform(get("/api/printers/export").param("search", "SN-CSV"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("printer-inventory-")))
                .andReturn().getResponse();
        String csv = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFFSerial Number,Sticker Number,Brand,Model,Supplier,Date of Purchase,Status"));
        assertTrue(csv.contains("\"Office, \"\"Supply\"\" Co.\",\"2026-09-15\""));
        assertTrue(csv.contains("\"Comma, \"\"quote\"\"\nnext\""));
        assertTrue(csv.contains("\"SN-CSV-2\",\"\",\"Epson\""));
        assertEquals(2, csv.split("SN-CSV-", -1).length - 1);
    }

    @Test
    void csvImportPreviewsThenCreatesPrintersAndReusesMatchingLocations() throws Exception {
        long existingLocation = createLocation("ICT").get("id").asLong();
        String csv = "Serial Number,Sticker Number,Brand,Model,Supplier,Date of Purchase,Status,Department,Section,Building,Floor,Room,Location Description,Remarks\r\n"
                + "SN-IMPORT-1,,Epson,L5290,Office Supplies,2026-09-15,ACTIVE,ICT,Operations,Main,,101,,\"Comma, and \"\"quotes\"\"\"\r\n"
                + "SN-IMPORT-2,STICKER-2,Canon,MF3010,,,Retired,Finance,,,,,,\"Second line\nremarks\"\r\n";
        var file = new MockMultipartFile("file", "printers.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/printers/import/preview").file(file))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.validRows").value(2)).andExpect(jsonPath("$.invalidRows").value(0));
        mvc.perform(multipart("/api/printers/import/confirm").file(file))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(2));
        mvc.perform(get("/api/printers").param("search", "SN-IMPORT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].stickerNumber").value("STICKER-2"))
                .andExpect(jsonPath("$.content[1].supplier").value("Office Supplies"))
                .andExpect(jsonPath("$.content[1].dateOfPurchase").value("2026-09-15"));
        mvc.perform(get("/api/printers/import/template"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Supplier,Date of Purchase")));
        assertEquals(1, jdbc.queryForObject("select count(*) from printers where location_id = ?", Integer.class, existingLocation));
        assertEquals(2, jdbc.queryForObject("select count(*) from locations", Integer.class));
    }

    @Test
    void csvImportRejectsInvalidStatusesAndDuplicateSerialsWithoutWriting() throws Exception {
        long location = createLocation("ICT").get("id").asLong();
        createPrinter(location, "EXISTING", "SN-EXISTING", "ACTIVE");
        String csv = "Serial Number,Sticker Number,Brand,Model,Status,Department\n"
                + "SN-EXISTING,,Epson,L5290,ACTIVE,ICT\n"
                + "SN-DUP,,Epson,L5290,ACTIVE,ICT\n"
                + "SN-DUP,SECOND,Epson,L5290,DISPOSED,ICT\n"
                + "   ,,Epson,L5290,ACTIVE,ICT\n";
        var file = new MockMultipartFile("file", "invalid.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/printers/import/preview").file(file))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalRows").value(4))
                .andExpect(jsonPath("$.validRows").value(1)).andExpect(jsonPath("$.invalidRows").value(3))
                .andExpect(jsonPath("$.errors[0].message").value("Serial Number already exists."));
        mvc.perform(multipart("/api/printers/import/confirm").file(file))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(0));
        assertEquals(1, jdbc.queryForObject("select count(*) from printers", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "UNDER_REPAIR", "FOR_REPAIR", "STORAGE", "RETIRED"})
    void acceptsEachSupportedStatus(String printerStatus) throws Exception {
        long location = createLocation("ICT").get("id").asLong();
        assertEquals(printerStatus, createPrinter(location, "ICT-PRN-001", "SN-" + printerStatus, printerStatus).get("status").asText());
    }

    @Test
    void serialNumbersAreRequiredAndWhitespaceIsRejected() throws Exception {
        long location = createLocation("ICT").get("id").asLong();
        mvc.perform(post("/api/printers").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(printerBody(location, "ICT-PRN-001", "  ", "ACTIVE"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.serialNumber")
                        .value("Serial number is required"));
        mvc.perform(post("/api/printers").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(printerBody(location, "ICT-PRN-002", null, "ACTIVE"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.serialNumber")
                        .value("Serial number is required"));
    }

    @Test
    void serialNumberCannotBeClearedWhenEditing() throws Exception {
        long location = createLocation("ICT").get("id").asLong();
        JsonNode printer = createPrinter(location, "ICT-PRN-SERIAL", "SN-VALID", "ACTIVE");
        Map<String, Object> edit = printerBody(location, "ICT-PRN-SERIAL", "   ", "ACTIVE");
        edit.put("version", printer.get("version").asLong());
        mvc.perform(put("/api/printers/{id}", printer.get("id").asLong()).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(edit)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.serialNumber")
                        .value("Serial number is required"));
    }

    @Test
    void stickerNumbersAreOptionalAndCanBeCleared() throws Exception {
        long location = createLocation("ICT").get("id").asLong();
        JsonNode first = createPrinter(location, null, "SN-OPTIONAL-1", "ACTIVE");
        JsonNode second = createPrinter(location, "ICT-PRN-OPTIONAL", "SN-OPTIONAL-2", "ACTIVE");
        assertTrue(first.get("stickerNumber").isNull());
        Map<String, Object> edit = printerBody(location, null, "SN-OPTIONAL-2", "ACTIVE");
        edit.put("version", second.get("version").asLong());
        mvc.perform(put("/api/printers/{id}", second.get("id").asLong()).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(edit)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stickerNumber").value(nullValue()));
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
                        .content(json.writeValueAsString(printerBody(Long.MAX_VALUE, "ICT-PRN-001", "SN-MISSING-LOCATION", "ACTIVE"))))
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
        long printer = createPrinter(location, "ICT-PRN-001", "SN-001", "ACTIVE").get("id").asLong();
        mvc.perform(put("/api/printers/{id}", printer).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(printerBody(location, "ICT-PRN-001", null, "ACTIVE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void databaseEnforcesStickerUniquenessEvenOutsideTheService() throws Exception {
        long location = createLocation("ICT").get("id").asLong();
        createPrinter(location, "ICT-PRN-001", "SN-001", "ACTIVE");
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
