package com.minierp.platform.security.session;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.web.SecurityApiErrorWriter;

/** Enforces session validity and database-backed permission freshness. / Áp dụng hiệu lực session và làm mới quyền từ database. */
public class SessionLifecycleFilter extends OncePerRequestFilter {

    private final SessionLifecycleService sessionLifecycleService;
    private final SecurityContextFreshnessService freshnessService;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityApiErrorWriter errorWriter;

    public SessionLifecycleFilter(
            SessionLifecycleService sessionLifecycleService,
            SecurityContextFreshnessService freshnessService,
            SecurityContextRepository securityContextRepository,
            SecurityApiErrorWriter errorWriter) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.freshnessService = freshnessService;
        this.securityContextRepository = securityContextRepository;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (!sessionLifecycleService.isValid(principal.userId(), session)) {
            denyAndInvalidate(request, response, session);
            return;
        }

        try {
            AuthenticatedPrincipal freshPrincipal = freshnessService.loadActivePrincipal(principal.userId()).orElse(null);
            if (freshPrincipal == null) {
                denyAndInvalidate(request, response, session);
                return;
            }
            Authentication freshAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                    freshPrincipal, null, freshPrincipal.authorities());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(freshAuthentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
            filterChain.doFilter(request, response);
        } catch (RuntimeException exception) {
            // A permission-refresh read failure must never preserve stale access.
            denyAndInvalidate(request, response, session);
        }
    }

    private void denyAndInvalidate(HttpServletRequest request, HttpServletResponse response, HttpSession session) throws IOException {
        sessionLifecycleService.invalidate(session);
        SecurityContextHolder.clearContext();
        errorWriter.write(response, HttpStatus.UNAUTHORIZED.value(), "UNAUTHENTICATED", "Authentication is required.");
    }
}
