package com.example.printerinventory.security;

import com.example.printerinventory.repository.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class UserStateFilter extends OncePerRequestFilter {
    private final AppUserRepository users;

    public UserStateFilter(AppUserRepository users) { this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof InventoryUserPrincipal principal) {
            var current = users.findById(principal.id()).orElse(null);
            if (current == null || !current.isEnabled()) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
            } else {
                var refreshed = new InventoryUserPrincipal(current);
                var updated = UsernamePasswordAuthenticationToken.authenticated(
                        refreshed, refreshed.getPassword(), refreshed.getAuthorities());
                updated.setDetails(authentication.getDetails());
                SecurityContextHolder.getContext().setAuthentication(updated);
            }
        }
        chain.doFilter(request, response);
    }
}
