package com.minierp.platform.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.minierp.platform.security.SecurityConfiguration;
import com.minierp.platform.security.session.SecurityContextFreshnessService;
import com.minierp.platform.security.session.SessionLifecycleService;
import com.minierp.platform.security.web.SecurityApiErrorWriter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the public status route while preserving the default-deny API boundary. / Kiểm tra status route công khai nhưng vẫn giữ ranh giới API mặc định từ chối.
 */
@WebMvcTest(StatusController.class)
@Import({SecurityConfiguration.class, SecurityApiErrorWriter.class, SessionLifecycleService.class})
class StatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private SecurityContextFreshnessService securityContextFreshnessService;

    @Test
    void returnsSafeStatus() throws Exception {
        mockMvc.perform(get("/api/v1/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.application").value("mini-erp-backend"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void returnsUnauthorizedForAnUnauthenticatedNonPublicApiRoute() throws Exception {
        mockMvc.perform(get("/api/v1/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    @WithMockUser(username = "security-test-user")
    void returnsForbiddenForAnAuthenticatedRequestWithoutAnAllowedCapability() throws Exception {
        mockMvc.perform(get("/api/v1/protected"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnsafeRequestsWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/status"))
                .andExpect(status().isForbidden());
    }

    @Test
    void doesNotCreateSpringSecurityGeneratedUser() {
        org.assertj.core.api.Assertions.assertThat(applicationContext.getBeansOfType(UserDetailsService.class)).isEmpty();
    }
}
