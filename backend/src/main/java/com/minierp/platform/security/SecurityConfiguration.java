package com.minierp.platform.security;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.web.context.SecurityContextHolderFilter;

import com.minierp.platform.security.bootstrap.AdminBootstrapProperties;
import com.minierp.platform.security.session.SessionLifecycleFilter;
import com.minierp.platform.security.session.SessionLifecycleProperties;
import com.minierp.platform.security.session.SessionRegistryCleanupListener;
import com.minierp.platform.security.session.SecurityContextFreshnessService;
import com.minierp.platform.security.web.SecurityApiErrorWriter;
import com.minierp.platform.security.web.SessionBoundCookieCsrfTokenRepository;
import com.minierp.platform.security.web.SpaCsrfTokenRequestHandler;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configures the API's deny-by-default security boundary. / Cấu hình ranh giới bảo mật mặc định từ chối API.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties({AdminBootstrapProperties.class, SessionLifecycleProperties.class})
public class SecurityConfiguration {

    private final SecurityApiErrorWriter securityApiErrorWriter;

    public SecurityConfiguration(SecurityApiErrorWriter securityApiErrorWriter) {
        this.securityApiErrorWriter = securityApiErrorWriter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new SessionBoundCookieCsrfTokenRepository();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new SessionFixationProtectionStrategy();
    }

    @Bean
    Clock securityClock() {
        return Clock.systemUTC();
    }

    @Bean
    SessionLifecycleFilter sessionLifecycleFilter(
            com.minierp.platform.security.session.SessionLifecycleService sessionLifecycleService,
            SecurityContextFreshnessService securityContextFreshnessService,
            SecurityContextRepository securityContextRepository) {
        return new SessionLifecycleFilter(
                sessionLifecycleService,
                securityContextFreshnessService,
                securityContextRepository,
                securityApiErrorWriter);
    }

    @Bean
    FilterRegistrationBean<SessionLifecycleFilter> sessionLifecycleFilterRegistration(SessionLifecycleFilter sessionLifecycleFilter) {
        FilterRegistrationBean<SessionLifecycleFilter> registration = new FilterRegistrationBean<>(sessionLifecycleFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    ServletListenerRegistrationBean<SessionRegistryCleanupListener> sessionRegistryCleanupListener(
            com.minierp.platform.security.session.SessionLifecycleService sessionLifecycleService) {
        return new ServletListenerRegistrationBean<>(new SessionRegistryCleanupListener(sessionLifecycleService));
    }

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, SessionLifecycleFilter sessionLifecycleFilter) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .securityContext(context -> context
                        .requireExplicitSave(true)
                        .securityContextRepository(securityContextRepository()))
                .sessionManagement(session -> session.sessionFixation().none())
                .addFilterAfter(sessionLifecycleFilter, SecurityContextHolderFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/v1/status", "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/users/**").authenticated()
                        .requestMatchers("/api/v1/categories/**", "/api/v1/products/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                securityApiErrorWriter.write(response, HttpStatus.UNAUTHORIZED.value(), "UNAUTHENTICATED", "Authentication is required."))
                        .accessDeniedHandler((request, response, exception) -> {
                            String code = exception instanceof CsrfException ? "CSRF_TOKEN_INVALID" : "ACCESS_DENIED";
                            String message = exception instanceof CsrfException ? "The CSRF token is invalid." : "Access is denied.";
                            securityApiErrorWriter.write(response, HttpStatus.FORBIDDEN.value(), code, message);
                        }))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
