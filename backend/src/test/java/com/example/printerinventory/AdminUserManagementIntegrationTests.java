package com.example.printerinventory;

import com.example.printerinventory.entity.*;
import com.example.printerinventory.repository.AppUserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
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
class AdminUserManagementIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;

    @Test
    void userManagementIsAdminOnlyAndResponsesNeverExposeHashes() throws Exception {
        var userSession = login(create("regular.user", "RegularPassword1!", UserRole.USER, true), "RegularPassword1!");
        mvc.perform(get("/api/admin/users").session(userSession)).andExpect(status().isForbidden());

        var adminSession = login(create("access.admin", "Administrator1!", UserRole.ADMIN, true), "Administrator1!");
        mvc.perform(get("/api/admin/users").session(adminSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void adminCreatesEditsAndRejectsDuplicateUsers() throws Exception {
        var session = login(create("manage.admin", "Administrator1!", UserRole.ADMIN, true), "Administrator1!");
        String create = """
                {"fullName":" New User ","username":" new.user ","password":"NewUserPassword1!","confirmPassword":"NewUserPassword1!","role":"USER","enabled":true}
                """;
        mvc.perform(post("/api/admin/users").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("new.user"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        AppUser created = users.findByUsernameIgnoreCase("new.user").orElseThrow();
        assertTrue(passwords.matches("NewUserPassword1!", created.getPasswordHash()));

        mvc.perform(post("/api/admin/users").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Username already exists."));

        mvc.perform(put("/api/admin/users/{id}", created.getId()).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Renamed User\",\"username\":\"renamed.user\",\"role\":\"ADMIN\",\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Renamed User"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void adminCanDisableEnableAndResetPassword() throws Exception {
        var adminSession = login(create("password.admin", "Administrator1!", UserRole.ADMIN, true), "Administrator1!");
        AppUser user = create("password.user", "OldPassword12!", UserRole.USER, true);
        var userSession = login(user, "OldPassword12!");

        mvc.perform(put("/api/admin/users/{id}/status", user.getId()).session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(get("/api/printers").session(userSession)).andExpect(status().isUnauthorized());
        failedLogin("password.user", "OldPassword12!");

        mvc.perform(put("/api/admin/users/{id}/status", user.getId()).session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        login(user, "OldPassword12!");

        mvc.perform(put("/api/admin/users/{id}/password", user.getId()).session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Replacement12!\",\"confirmPassword\":\"Replacement12!\"}"))
                .andExpect(status().isNoContent());
        failedLogin("password.user", "OldPassword12!");
        login(user, "Replacement12!");
    }

    @Test
    void lastActiveAdministratorCannotBeDisabledOrDemoted() throws Exception {
        AppUser first = create("last.admin", "Administrator1!", UserRole.ADMIN, true);
        var session = login(first, "Administrator1!");
        mvc.perform(put("/api/admin/users/{id}/status", first.getId()).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("At least one active administrator must remain."));
        mvc.perform(put("/api/admin/users/{id}", first.getId()).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Last Admin\",\"username\":\"last.admin\",\"role\":\"USER\",\"enabled\":true}"))
                .andExpect(status().isConflict());

        create("second.admin", "Administrator2!", UserRole.ADMIN, true);
        mvc.perform(put("/api/admin/users/{id}", first.getId()).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Former Admin\",\"username\":\"last.admin\",\"role\":\"USER\",\"enabled\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/printers").session(session)).andExpect(status().isOk());
    }

    @Test
    void validationAndMissingUsersReturnSafeErrors() throws Exception {
        var session = login(create("validation.admin", "Administrator1!", UserRole.ADMIN, true), "Administrator1!");
        mvc.perform(post("/api/admin/users").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\" \",\"username\":\" \",\"password\":\"short\",\"confirmPassword\":\"different\",\"role\":\"USER\",\"enabled\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.username").exists()).andExpect(jsonPath("$.fieldErrors.password").exists());
        mvc.perform(put("/api/admin/users/999999/password").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Replacement12!\",\"confirmPassword\":\"Replacement12!\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("User not found."));
    }

    private AppUser create(String username, String password, UserRole role, boolean enabled) {
        return users.saveAndFlush(new AppUser(username, passwords.encode(password), username, role, enabled));
    }
    private MockHttpSession login(AppUser user, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", user.getUsername()).param("password", password))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }
    private void failedLogin(String username, String password) throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", username).param("password", password))
                .andExpect(status().isUnauthorized());
    }
}
