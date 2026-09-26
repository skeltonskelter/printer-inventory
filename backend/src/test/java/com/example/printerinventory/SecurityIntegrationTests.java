package com.example.printerinventory;

import com.example.printerinventory.entity.AppUser;
import com.example.printerinventory.entity.UserRole;
import com.example.printerinventory.repository.AppUserRepository;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
class SecurityIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;

    @Test
    void publicHealthAndCsrfRemainAvailableWhileInventoryRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(get("/api/printers")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/locations").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"department\":\"ICT\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void userAndAdminCanAuthenticateAndAccessInventory() throws Exception {
        create("inventory.user", "ValidPassword1!", "Inventory User", UserRole.USER, true);
        create("ict.admin", "ValidPassword2!", "ICT Administrator", UserRole.ADMIN, true);

        HttpSession userSession = login(" inventory.user ", "ValidPassword1!");
        mvc.perform(get("/api/auth/me").session((org.springframework.mock.web.MockHttpSession) userSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.fullName").value("Inventory User"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/printers").session((org.springframework.mock.web.MockHttpSession) userSession))
                .andExpect(status().isOk());

        HttpSession adminSession = login("ict.admin", "ValidPassword2!");
        mvc.perform(get("/api/printers").session((org.springframework.mock.web.MockHttpSession) adminSession))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session((org.springframework.mock.web.MockHttpSession) adminSession))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void invalidUnknownAndDisabledCredentialsUseTheSameSafeFailure() throws Exception {
        create("enabled.user", "ValidPassword1!", "Enabled User", UserRole.USER, true);
        create("disabled.user", "ValidPassword2!", "Disabled User", UserRole.USER, false);
        failedLogin("enabled.user", "wrong-password");
        failedLogin("unknown.user", "wrong-password");
        failedLogin("disabled.user", "ValidPassword2!");
    }

    @Test
    void logoutInvalidatesTheAuthenticatedSession() throws Exception {
        create("logout.user", "ValidPassword1!", "Logout User", UserRole.USER, true);
        var session = (org.springframework.mock.web.MockHttpSession) login("logout.user", "ValidPassword1!");
        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/printers").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void userRoleCanUseProtectedWriteAndCsvEndpointsWhenCsrfIsValid() throws Exception {
        create("write.user", "ValidPassword1!", "Write User", UserRole.USER, true);
        var session = (org.springframework.mock.web.MockHttpSession) login("write.user", "ValidPassword1!");

        mvc.perform(post("/api/locations").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"department\":\"Security Test\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/printers/export").session(session)).andExpect(status().isOk());
        var csv = new MockMultipartFile("file", "invalid.csv", "text/csv",
                "Serial Number,Brand,Model,Status,Department\r\n".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/printers/import/preview").file(csv).session(session).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void passwordsAreStoredOnlyAsBcryptHashes() {
        AppUser user = create("hash.user", "ValidPassword1!", "Hash User", UserRole.USER, true);
        assertNotEquals("ValidPassword1!", user.getPasswordHash());
        assertTrue(user.getPasswordHash().startsWith("$2"));
        assertTrue(passwords.matches("ValidPassword1!", user.getPasswordHash()));
    }

    private AppUser create(String username, String password, String fullName, UserRole role, boolean enabled) {
        return users.saveAndFlush(new AppUser(username, passwords.encode(password), fullName, role, enabled));
    }

    private HttpSession login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).param("username", username).param("password", password))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }

    private void failedLogin(String username, String password) throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", username).param("password", password))
                .andExpect(status().isUnauthorized());
    }
}
