package com.minierp.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.persistence.UserAccountRepository;
import com.minierp.platform.security.web.SecurityApiErrorWriter;

/** Covers fail-closed freshness behavior with mocked persistence reads. / Kiểm tra behavior refresh quyền fail-closed với database mock. */
class SessionLifecycleFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refreshesAuthoritiesFromTheCurrentDatabasePrincipal() throws Exception {
        UUID userId = UUID.randomUUID();
        SessionLifecycleService lifecycle = lifecycle();
        SecurityContextFreshnessService freshness = mock(SecurityContextFreshnessService.class);
        AuthenticatedPrincipal refreshed = principal(userId, "ROLE_MANAGER");
        when(freshness.loadActivePrincipal(userId)).thenReturn(Optional.of(refreshed));
        SessionLifecycleFilter filter = filter(lifecycle, freshness);
        MockHttpServletRequest request = authenticatedRequest(lifecycle, userId, "ROLE_SALES_STAFF");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_MANAGER");
    }

    @Test
    void deactivatedOrMissingUserIsDeniedOnTheNextRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        SessionLifecycleService lifecycle = lifecycle();
        SecurityContextFreshnessService freshness = mock(SecurityContextFreshnessService.class);
        when(freshness.loadActivePrincipal(userId)).thenReturn(Optional.empty());
        SessionLifecycleFilter filter = filter(lifecycle, freshness);
        MockHttpServletRequest request = authenticatedRequest(lifecycle, userId, "ROLE_ADMIN");
        MockHttpSession session = (MockHttpSession) request.getSession(false);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void databaseFailureFailsClosedAndInvalidatesTheSession() throws Exception {
        UUID userId = UUID.randomUUID();
        SessionLifecycleService lifecycle = lifecycle();
        SecurityContextFreshnessService freshness = mock(SecurityContextFreshnessService.class);
        when(freshness.loadActivePrincipal(userId)).thenThrow(new RuntimeException("database unavailable"));
        SessionLifecycleFilter filter = filter(lifecycle, freshness);
        MockHttpServletRequest request = authenticatedRequest(lifecycle, userId, "ROLE_ADMIN");
        MockHttpSession session = (MockHttpSession) request.getSession(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(session.isInvalid()).isTrue();
    }

    private SessionLifecycleFilter filter(SessionLifecycleService lifecycle, SecurityContextFreshnessService freshness) {
        return new SessionLifecycleFilter(
                lifecycle,
                freshness,
                new HttpSessionSecurityContextRepository(),
                new SecurityApiErrorWriter(new ObjectMapper().findAndRegisterModules()));
    }

    private MockHttpServletRequest authenticatedRequest(
            SessionLifecycleService lifecycle, UUID userId, String authority) {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest registrationRequest = new MockHttpServletRequest();
        registrationRequest.setSession(session);
        lifecycle.registerAuthenticatedSession(userId, registrationRequest);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        AuthenticatedPrincipal principal = principal(userId, authority);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities()));
        return request;
    }

    private SessionLifecycleService lifecycle() {
        SessionLifecycleProperties properties = new SessionLifecycleProperties();
        return new SessionLifecycleService(properties, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    private AuthenticatedPrincipal principal(UUID userId, String authority) {
        return new AuthenticatedPrincipal(userId, "user", "User", Set.of(new SimpleGrantedAuthority(authority)));
    }
}
