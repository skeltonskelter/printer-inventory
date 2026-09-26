package com.example.printerinventory;

import com.example.printerinventory.entity.*;
import com.example.printerinventory.repository.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Tag("integration")
class AuditTrailIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired AuditLogRepository audits;
    @Autowired PasswordEncoder passwords;

    @Test
    void auditApiIsAdminOnlyAndLoginLogoutAreRecorded() throws Exception {
        var userSession = login(createUser("audit.regular", UserRole.USER), "Administrator1!");
        mvc.perform(get("/api/admin/audit-logs").session(userSession)).andExpect(status().isForbidden());

        var adminSession = login(createUser("audit.admin", UserRole.ADMIN), "Administrator1!");
        mvc.perform(get("/api/admin/audit-logs").session(adminSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.content[0].timestamp").exists());
        mvc.perform(post("/api/auth/logout").session(adminSession).with(csrf())).andExpect(status().isNoContent());
        assertTrue(audits.findAll().stream().anyMatch(log -> log.getUsername().equals("audit.admin") && log.getAction() == AuditAction.LOGIN));
        assertTrue(audits.findAll().stream().anyMatch(log -> log.getUsername().equals("audit.admin") && log.getAction() == AuditAction.LOGOUT));
        assertTrue(audits.findAll().stream().filter(log -> log.getUsername().equals("audit.admin"))
                .allMatch(log -> "audit.admin".equals(log.getEntityIdentifier())));
    }

    @Test
    void printerLocationRelocationAndImportEventsContainUsefulChanges() throws Exception {
        var session = login(createUser("business.admin", UserRole.ADMIN), "Administrator1!");
        var historical = audits.saveAndFlush(new AuditLog(java.time.Instant.now(), null, "historical", "ADMIN",
                AuditAction.UPDATE, AuditEntityType.PRINTER, "789", "789", "Historical event.", null, null));
        long firstLocation = createLocation(session, "Accounting");
        long secondLocation = createLocation(session, "Billing");
        long disposableLocation = createLocation(session, "Temporary");
        mvc.perform(delete("/api/locations/{id}", disposableLocation).session(session).with(csrf()))
                .andExpect(status().isNoContent());

        String createPrinter = "{\"brand\":\"HP\",\"model\":\"M404\",\"supplier\":\"Supplier\",\"dateOfPurchase\":\"2026-09-01\",\"serialNumber\":\"AUDIT-SERIAL-1\",\"stickerNumber\":null,\"locationId\":" + firstLocation + ",\"status\":\"ACTIVE\",\"remarks\":null}";
        var created = mvc.perform(post("/api/printers").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(createPrinter))
                .andExpect(status().isCreated()).andReturn();
        long printerId = ((Number) com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        int version = ((Number) com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.version")).intValue();

        String update = "{\"brand\":\"HP\",\"model\":\"M404\",\"supplier\":\"Supplier\",\"dateOfPurchase\":\"2026-09-01\",\"serialNumber\":\"AUDIT-SERIAL-1\",\"stickerNumber\":null,\"locationId\":" + firstLocation + ",\"status\":\"FOR_REPAIR\",\"remarks\":null,\"version\":" + version + "}";
        var updated = mvc.perform(put("/api/printers/{id}", printerId).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk()).andReturn();
        version = ((Number) com.jayway.jsonpath.JsonPath.read(updated.getResponse().getContentAsString(), "$.version")).intValue();
        String relocate = "{\"newLocationId\":" + secondLocation + ",\"relocationDate\":\"2026-09-25\",\"remarks\":\"Department transfer\",\"version\":" + version + "}";
        mvc.perform(post("/api/printers/{id}/relocate", printerId).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(relocate))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/printers/{id}", printerId).session(session).with(csrf())).andExpect(status().isNoContent());

        var csv = new MockMultipartFile("file", "audit.csv", "text/csv",
                "Serial Number,Sticker Number,Brand,Model,Status,Department\r\nAUDIT-IMPORT-1,,Canon,LBP,ACTIVE,Imported\r\n".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/printers/import/confirm").file(csv).session(session).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(1));

        mvc.perform(get("/api/admin/audit-logs").session(session).param("action", "UPDATE").param("entityType", "PRINTER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].oldValues.status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].newValues.status").value("FOR_REPAIR"));
        assertTrue(audits.findAll().stream().anyMatch(log -> log.getAction() == AuditAction.RELOCATE
                && log.getOldValues().contains("Accounting") && log.getNewValues().contains("Billing")));
        assertTrue(audits.findAll().stream().anyMatch(log -> log.getAction() == AuditAction.IMPORT
                && "CSV Import".equals(log.getEntityIdentifier()) && log.getNewValues().contains("\"importedRows\":1")));
        for (AuditAction action : java.util.List.of(AuditAction.CREATE, AuditAction.UPDATE, AuditAction.DELETE)) {
            assertTrue(audits.findAll().stream().anyMatch(log -> log.getAction() == action
                    && log.getEntityType() == AuditEntityType.PRINTER
                    && "AUDIT-SERIAL-1".equals(log.getEntityIdentifier())));
        }
        assertTrue(audits.findAll().stream().anyMatch(log -> log.getAction() == AuditAction.RELOCATE
                && "AUDIT-SERIAL-1".equals(log.getEntityIdentifier())));
        assertEquals("789", audits.findById(historical.getId()).orElseThrow().getEntityIdentifier());
    }

    @Test
    void userManagementEventsNeverContainPasswords() throws Exception {
        var session = login(createUser("users.admin", UserRole.ADMIN), "Administrator1!");
        String create = "{\"fullName\":\"Audit User\",\"username\":\"managed.audit\",\"password\":\"VerySecret123!\",\"confirmPassword\":\"VerySecret123!\",\"role\":\"USER\",\"enabled\":true}";
        var result = mvc.perform(post("/api/admin/users").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated()).andReturn();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        mvc.perform(put("/api/admin/users/{id}", id).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Audit User Renamed\",\"username\":\"managed.audit\",\"role\":\"ADMIN\",\"enabled\":true}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/admin/users/{id}/status", id).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/admin/users/{id}/password", id).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"Replacement12!\",\"confirmPassword\":\"Replacement12!\"}"))
                .andExpect(status().isNoContent());
        String auditText = audits.findAll().toString() + audits.findAll().stream()
                .map(log -> String.valueOf(log.getOldValues()) + log.getNewValues() + log.getDescription()).reduce("", String::concat);
        assertFalse(auditText.contains("VerySecret123!")); assertFalse(auditText.contains("Replacement12!"));
        assertTrue(audits.findAll().stream().anyMatch(log -> log.getAction() == AuditAction.USER_CREATE));
        assertTrue(audits.findAll().stream().anyMatch(log -> log.getAction() == AuditAction.ROLE_CHANGE));
        assertTrue(audits.findAll().stream().anyMatch(log -> log.getAction() == AuditAction.USER_DISABLE));
        assertTrue(audits.findAll().stream().anyMatch(log -> log.getAction() == AuditAction.PASSWORD_RESET && log.getOldValues() == null && log.getNewValues() == null));
        assertTrue(audits.findAll().stream().filter(log -> log.getEntityType() == AuditEntityType.USER)
                .allMatch(log -> "managed.audit".equals(log.getEntityIdentifier())));
    }

    private long createLocation(MockHttpSession session, String department) throws Exception {
        var result = mvc.perform(post("/api/locations").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"department\":\"" + department + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }
    private AppUser createUser(String username, UserRole role) {
        return users.saveAndFlush(new AppUser(username, passwords.encode("Administrator1!"), username, role, true));
    }
    private MockHttpSession login(AppUser user, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", user.getUsername()).param("password", password))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }
}
