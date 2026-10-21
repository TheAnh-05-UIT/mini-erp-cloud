package com.minierp.platform.security.web;

import java.util.LinkedHashSet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minierp.platform.security.authentication.AuthenticatedPrincipal;
import com.minierp.platform.security.authentication.AuthenticationService;
import com.minierp.platform.security.authentication.SecurityAuthenticationAuditRecorder;
import com.minierp.platform.security.session.SessionLifecycleService;

/** Exposes only the approved session-authentication endpoints. / Chỉ cung cấp các endpoint session-authentication đã được phê duyệt. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SecurityAuthenticationAuditRecorder securityAuditRecorder;
    private final SessionLifecycleService sessionLifecycleService;

    public AuthenticationController(
            AuthenticationService authenticationService,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            SecurityAuthenticationAuditRecorder securityAuditRecorder,
            SessionLifecycleService sessionLifecycleService) {
        this.authenticationService = authenticationService;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.securityAuditRecorder = securityAuditRecorder;
        this.sessionLifecycleService = sessionLifecycleService;
    }

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        // Resolving the argument forces the configured repository to issue or refresh its cookie.
        csrfToken.getToken();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/login")
    public AuthenticationContextResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthenticatedPrincipal principal = authenticationService.authenticate(request.username(), request.password());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.authorities());

        // Audit has already succeeded; only now may Spring create/rotate an authenticated session.
        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);
        sessionLifecycleService.registerAuthenticatedSession(principal.userId(), httpRequest);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        SecurityContextHolder.clearContext();
        refreshCsrfToken(httpRequest, httpResponse);

        return new AuthenticationContextResponse(toCurrentUser(principal));
    }

    @GetMapping("/me")
    public AuthenticationContextResponse me(Authentication authentication) {
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        return new AuthenticationContextResponse(toCurrentUser(principal));
    }

    @PostMapping("/logout")
    public LogoutResponse logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        sessionLifecycleService.invalidate(request.getSession(false));
        SecurityContextHolder.clearContext();
        csrfTokenRepository.saveToken(null, request, response);
        securityAuditRecorder.recordLogout(principal.userId());
        return new LogoutResponse("LOGGED_OUT");
    }

    private CurrentUserResponse toCurrentUser(AuthenticatedPrincipal principal) {
        var roles = principal.authorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new CurrentUserResponse(principal.userId(), principal.username(), principal.displayName(), roles);
    }

    private void refreshCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        csrfTokenRepository.saveToken(null, request, response);
        csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);
    }
}
