package com.minierp.platform.catalog.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.minierp.platform.catalog.application.CatalogService;
import com.minierp.platform.security.SecurityConfiguration;
import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.authentication.AuthenticationService;
import com.minierp.platform.security.authentication.SecurityAuthenticationAuditRecorder;
import com.minierp.platform.security.session.SecurityContextFreshnessService;
import com.minierp.platform.security.session.SessionLifecycleService;
import com.minierp.platform.security.web.AuthenticationController;
import com.minierp.platform.security.web.SecurityApiErrorWriter;

/** Exercises Catalog HTTP security using the real session and CSRF flow. / Kiem tra bao mat Catalog qua session va CSRF that. */
@WebMvcTest({ AuthenticationController.class, CatalogController.class })
@Import({ SecurityConfiguration.class, SecurityApiErrorWriter.class, CatalogApiExceptionHandler.class,
        SessionLifecycleService.class })
class CatalogControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean CatalogService catalogService;
    @MockitoBean AuthenticationService authenticationService;
    @MockitoBean SecurityAuthenticationAuditRecorder securityAuditRecorder;
    @MockitoBean SecurityContextFreshnessService freshness;

    @BeforeEach void setUp() {
        when(authenticationService.authenticate("admin", "correct-password")).thenReturn(principal());
        when(freshness.loadActivePrincipal(any())).thenAnswer(call -> java.util.Optional.of(principal((UUID) call.getArgument(0))));
    }

    @Test void catalogRequiresAuthenticationAndCsrfForMutations() throws Exception {
        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/categories").session(client().session())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"OFFICE\",\"name\":\"Office\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test void unsupportedProductSortReturns400ForAuthenticatedSession() throws Exception {
        mockMvc.perform(get("/api/v1/products?sort=code").session(client().session()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private AuthenticatedClient client() {
        try {
            var csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
            MockHttpSession anonymous = (MockHttpSession) csrf.getRequest().getSession(false);
            var cookie = csrfCookie(csrf);
            var login = mockMvc.perform(post("/api/v1/auth/login").session(anonymous).cookie(cookie)
                    .header("X-XSRF-TOKEN", cookie.getValue()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"correct-password\"}"))
                    .andExpect(status().isOk()).andReturn();
            return new AuthenticatedClient((MockHttpSession) login.getRequest().getSession(false), csrfCookie(login));
        } catch (Exception exception) { throw new AssertionError(exception); }
    }

    private jakarta.servlet.http.Cookie csrfCookie(org.springframework.test.web.servlet.MvcResult result) {
        String value = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith("XSRF-TOKEN=") && !header.startsWith("XSRF-TOKEN=;"))
                .reduce((ignored, latest) -> latest).orElseThrow();
        return new jakarta.servlet.http.Cookie("XSRF-TOKEN", value.substring("XSRF-TOKEN=".length(), value.indexOf(';')));
    }

    private AuthenticatedPrincipal principal() { return principal(UUID.randomUUID()); }
    private AuthenticatedPrincipal principal(UUID id) { return new AuthenticatedPrincipal(id, "admin", "Admin", Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))); }
    private record AuthenticatedClient(MockHttpSession session, jakarta.servlet.http.Cookie csrfCookie) { }
}
