package com.minierp.platform.security.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.minierp.platform.security.SecurityConfiguration;
import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.authentication.AuthenticationService;
import com.minierp.platform.security.authentication.SecurityAuditPersistenceException;
import com.minierp.platform.security.authentication.SecurityAuthenticationAuditRecorder;
import com.minierp.platform.security.session.SecurityContextFreshnessService;
import com.minierp.platform.security.session.SessionLifecycleService;

/** Verifies the approved HTTP authentication and CSRF boundary without real credentials. / Kiểm tra ranh giới HTTP authentication và CSRF đã phê duyệt mà không dùng credentials thật. */
@WebMvcTest(AuthenticationController.class)
@Import({SecurityConfiguration.class, SecurityApiErrorWriter.class, AuthenticationApiExceptionHandler.class, SessionLifecycleService.class})
class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private SecurityAuthenticationAuditRecorder securityAuditRecorder;

    @MockitoBean
    private SecurityContextFreshnessService securityContextFreshnessService;

    @BeforeEach
    void refreshesTheAuthenticatedTestPrincipal() {
        when(securityContextFreshnessService.loadActivePrincipal(any()))
                .thenAnswer(invocation -> Optional.of(principal(invocation.getArgument(0))));
    }

    @Test
    void csrfBootstrapReturnsNoContentAndReadableTokenCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void loginWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void validLoginCreatesSessionAndMeReadsIt() throws Exception {
        when(authenticationService.authenticate("admin", "correct-password")).thenReturn(principal());
        CsrfBootstrap csrf = csrfBootstrap();
        String anonymousSessionId = csrf.session().getId();

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentUser.username").value("admin"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.currentUser.roles[0]").value("ADMIN"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        org.assertj.core.api.Assertions.assertThat(session.getId()).isNotEqualTo(anonymousSessionId);
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentUser.displayName").value("Administrator"));
    }

    @Test
    void invalidCredentialsStayGenericAndUnauthenticated() throws Exception {
        when(authenticationService.authenticate(any(), any())).thenThrow(new com.minierp.platform.security.authentication.AuthenticationFailedException());
        CsrfBootstrap csrf = csrfBootstrap();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"unknown\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andReturn();

        assertNoAuthenticatedContext((MockHttpSession) result.getRequest().getSession(false));
    }

    @Test
    void loginAuditFailureReturnsSafeServiceUnavailableWithoutSession() throws Exception {
        when(authenticationService.authenticate(any(), any())).thenThrow(new SecurityAuditPersistenceException(new RuntimeException("unavailable")));
        CsrfBootstrap csrf = csrfBootstrap();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_UNAVAILABLE"))
                .andReturn();

        assertNoAuthenticatedContext((MockHttpSession) result.getRequest().getSession(false));
    }

    @Test
    void unauthenticatedMeReturnsSafe401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void logoutWithCsrfInvalidatesTheServerSession() throws Exception {
        when(authenticationService.authenticate("admin", "correct-password")).thenReturn(principal());
        CsrfBootstrap csrf = csrfBootstrap();
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        jakarta.servlet.http.Cookie authenticatedCsrf = latestCsrfCookie(login);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .cookie(authenticatedCsrf)
                        .header("X-XSRF-TOKEN", authenticatedCsrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGGED_OUT"));
        org.assertj.core.api.Assertions.assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void rejectsOldCookieAndHeaderAfterSuccessfulLoginRotation() throws Exception {
        when(authenticationService.authenticate("admin", "correct-password")).thenReturn(principal());
        CsrfBootstrap beforeLogin = csrfBootstrap();
        MvcResult login = login(beforeLogin);
        MockHttpSession authenticatedSession = (MockHttpSession) login.getRequest().getSession(false);
        jakarta.servlet.http.Cookie afterLogin = latestCsrfCookie(login);

        org.assertj.core.api.Assertions.assertThat(afterLogin.getValue()).isNotEqualTo(beforeLogin.cookie().getValue());
        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(authenticatedSession)
                        .cookie(beforeLogin.cookie())
                        .header("X-XSRF-TOKEN", beforeLogin.cookie().getValue()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(authenticatedSession)
                        .cookie(afterLogin)
                        .header("X-XSRF-TOKEN", afterLogin.getValue()))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsOldTokenAfterLogoutAndAcceptsNewAnonymousBootstrapToken() throws Exception {
        when(authenticationService.authenticate("admin", "correct-password")).thenReturn(principal());
        CsrfBootstrap beforeLogin = csrfBootstrap();
        MvcResult login = login(beforeLogin);
        MockHttpSession authenticatedSession = (MockHttpSession) login.getRequest().getSession(false);
        jakarta.servlet.http.Cookie afterLogin = latestCsrfCookie(login);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(authenticatedSession)
                        .cookie(afterLogin)
                        .header("X-XSRF-TOKEN", afterLogin.getValue()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(afterLogin)
                        .header("X-XSRF-TOKEN", afterLogin.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"correct-password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        CsrfBootstrap afterLogout = csrfBootstrap();
        mockMvc.perform(post("/api/v1/auth/login")
                        .session(afterLogout.session())
                        .cookie(afterLogout.cookie())
                        .header("X-XSRF-TOKEN", afterLogout.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAnArbitraryCookieAndHeaderPairWithoutItsServerSideSessionToken() throws Exception {
        jakarta.servlet.http.Cookie attackerCookie = new jakarta.servlet.http.Cookie("XSRF-TOKEN", "attacker-controlled-value");

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(attackerCookie)
                        .header("X-XSRF-TOKEN", attackerCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"correct-password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    private CsrfBootstrap csrfBootstrap() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();
        return new CsrfBootstrap(
                latestCsrfCookie(result),
                (MockHttpSession) result.getRequest().getSession(false));
    }

    private MvcResult login(CsrfBootstrap csrf) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .session(csrf.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void assertNoAuthenticatedContext(MockHttpSession session) {
        org.assertj.core.api.Assertions.assertThat(session).isNotNull();
        org.assertj.core.api.Assertions.assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
    }

    private jakarta.servlet.http.Cookie latestCsrfCookie(MvcResult result) {
        String cookieHeader = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith("XSRF-TOKEN="))
                .reduce((ignored, latest) -> latest)
                .orElseThrow();
        String value = cookieHeader.substring("XSRF-TOKEN=".length(), cookieHeader.indexOf(';'));
        return new jakarta.servlet.http.Cookie("XSRF-TOKEN", value);
    }

    private record CsrfBootstrap(jakarta.servlet.http.Cookie cookie, MockHttpSession session) {
    }

    private AuthenticatedPrincipal principal() {
        return principal(UUID.randomUUID());
    }

    private AuthenticatedPrincipal principal(UUID userId) {
        return new AuthenticatedPrincipal(userId, "admin", "Administrator", Set.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
