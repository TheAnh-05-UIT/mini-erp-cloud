package com.minierp.platform.security.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.minierp.platform.security.SecurityConfiguration;
import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.authentication.AuthenticationService;
import com.minierp.platform.security.authentication.SecurityAuthenticationAuditRecorder;
import com.minierp.platform.security.persistence.RoleCode;
import com.minierp.platform.security.session.SecurityContextFreshnessService;
import com.minierp.platform.security.session.SessionLifecycleService;
import com.minierp.platform.security.useradmin.DuplicateUsernameException;
import com.minierp.platform.security.useradmin.UserAdministrationResult;
import com.minierp.platform.security.useradmin.UserAdministrationService;

/** Verifies User Administration HTTP authorization and safe DTO mapping. / Kiem tra authorization HTTP va DTO an toan cua Quan tri nguoi dung. */
@WebMvcTest({AuthenticationController.class, UserAdministrationController.class})
@Import({SecurityConfiguration.class, SecurityApiErrorWriter.class, AuthenticationApiExceptionHandler.class,
        UserAdministrationApiExceptionHandler.class, SessionLifecycleService.class})
class UserAdministrationControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean UserAdministrationService service;
    @MockitoBean AuthenticationService authenticationService;
    @MockitoBean SecurityAuthenticationAuditRecorder securityAuditRecorder;
    @MockitoBean SecurityContextFreshnessService freshness;

    @BeforeEach void refreshesPrincipal() {
        when(freshness.loadActivePrincipal(any())).thenAnswer(call ->
                java.util.Optional.of(principal((UUID) call.getArgument(0))));
        when(authenticationService.authenticate("admin", "correct-password")).thenReturn(adminPrincipal());
    }

    @Test void unauthenticatedUserApiReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }

    @Test void adminCreatesUserWithCsrfAndSafeResponse() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any(), any())).thenReturn(new UserAdministrationResult(id, "sales.user", "Sales User", true, Set.of(RoleCode.SALES_STAFF)));
        AuthenticatedClient client = authenticatedClient();
        mockMvc.perform(post("/api/v1/users").session(client.session()).cookie(client.csrfCookie()).header("X-XSRF-TOKEN", client.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Sales.User\",\"displayName\":\"Sales User\",\"initialPassword\":\"test-only-password\",\"roleCodes\":[\"SALES_STAFF\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("sales.user"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.initialPassword").doesNotExist());
    }

    @Test void mutationWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/users").session(authenticatedClient().session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user\",\"displayName\":\"User\",\"initialPassword\":\"test-only-password\",\"roleCodes\":[\"SALES_STAFF\"]}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test void invalidRoleCodeReturns400() throws Exception {
        AuthenticatedClient client = authenticatedClient();
        mockMvc.perform(put("/api/v1/users/{id}/roles", UUID.randomUUID()).session(client.session()).cookie(client.csrfCookie()).header("X-XSRF-TOKEN", client.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"UNKNOWN\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test void duplicateUsernameReturns409() throws Exception {
        when(service.create(any(), any())).thenThrow(new DuplicateUsernameException());
        AuthenticatedClient client = authenticatedClient();
        mockMvc.perform(post("/api/v1/users").session(client.session()).cookie(client.csrfCookie()).header("X-XSRF-TOKEN", client.csrfCookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user\",\"displayName\":\"User\",\"initialPassword\":\"test-only-password\",\"roleCodes\":[\"SALES_STAFF\"]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));
    }

    private UsernamePasswordAuthenticationToken admin() {
        AuthenticatedPrincipal principal = adminPrincipal();
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities());
    }

    private AuthenticatedPrincipal adminPrincipal() {
        return principal(UUID.randomUUID());
    }

    private AuthenticatedPrincipal principal(UUID userId) {
        return new AuthenticatedPrincipal(userId, "admin", "Admin", Set.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private AuthenticatedClient authenticatedClient() {
        try {
            var csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
            MockHttpSession anonymous = (MockHttpSession) csrf.getRequest().getSession(false);
            String csrfValue = csrfCookie(csrf).getValue();
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("XSRF-TOKEN", csrfValue);
            var login = mockMvc.perform(post("/api/v1/auth/login")
                            .session(anonymous).cookie(cookie).header("X-XSRF-TOKEN", csrfValue)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"correct-password\"}"))
                    .andExpect(status().isOk()).andReturn();
            return new AuthenticatedClient((MockHttpSession) login.getRequest().getSession(false), csrfCookie(login));
        } catch (Exception exception) {
            throw new AssertionError("Could not create authenticated test session", exception);
        }
    }

    private jakarta.servlet.http.Cookie csrfCookie(org.springframework.test.web.servlet.MvcResult result) {
        String header = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith("XSRF-TOKEN=") && !value.startsWith("XSRF-TOKEN=;"))
                .reduce((ignored, latest) -> latest)
                .orElseThrow();
        return new jakarta.servlet.http.Cookie("XSRF-TOKEN", header.substring("XSRF-TOKEN=".length(), header.indexOf(';')));
    }

    private record AuthenticatedClient(MockHttpSession session, jakarta.servlet.http.Cookie csrfCookie) { }
}
