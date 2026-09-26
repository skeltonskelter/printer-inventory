package com.example.printerinventory.config;

import com.example.printerinventory.security.InventoryUserDetailsService;
import com.example.printerinventory.security.InventoryUserPrincipal;
import com.example.printerinventory.security.UserStateFilter;
import com.example.printerinventory.entity.AuditAction;
import com.example.printerinventory.service.AuditService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class SecurityConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    DaoAuthenticationProvider authenticationProvider(InventoryUserDetailsService users,
                                                     PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            DaoAuthenticationProvider authenticationProvider,
                                            UserStateFilter userStateFilter,
                                            AuditService audit) throws Exception {
        var csrfRepository = new HttpSessionCsrfTokenRepository();

        http
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health", "/api/auth/csrf", "/api/auth/login").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) -> {
                            auditAuthentication(audit, authentication.getPrincipal(), AuditAction.LOGIN);
                            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        })
                        .failureHandler((request, response, exception) ->
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            if (authentication != null) auditAuthentication(audit, authentication.getPrincipal(), AuditAction.LOGOUT);
                            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        }))
                .requestCache(cache -> cache.disable())
                .addFilterBefore(userStateFilter, AuthorizationFilter.class)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN)));
        return http.build();
    }

    private static void auditAuthentication(AuditService audit, Object principal, AuditAction action) {
        if (!(principal instanceof InventoryUserPrincipal user)) return;
        try { audit.recordAuthentication(user, action); }
        catch (RuntimeException exception) { log.error("Could not record {} audit event for user {}.", action, user.getUsername(), exception); }
    }
}
