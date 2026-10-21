package com.minierp.platform.security.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/**
 * Stores the authoritative CSRF token in HttpSession and mirrors it only for SPA header transport.
 * Lưu CSRF token có thẩm quyền trong HttpSession, cookie chỉ phục vụ SPA gửi header.
 */
public final class SessionBoundCookieCsrfTokenRepository implements CsrfTokenRepository {

    private static final String COOKIE_NAME = "XSRF-TOKEN";

    private final HttpSessionCsrfTokenRepository sessionRepository = sessionRepository();

    private HttpSessionCsrfTokenRepository sessionRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-XSRF-TOKEN");
        return repository;
    }

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return sessionRepository.generateToken(request);
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        sessionRepository.saveToken(token, request, response);
        response.addHeader("Set-Cookie", cookie(token == null ? "" : token.getToken(), request, token == null).toString());
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        return sessionRepository.loadToken(request);
    }

    private ResponseCookie cookie(String value, HttpServletRequest request, boolean delete) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, value)
                .path("/")
                .httpOnly(false)
                .secure(request.isSecure())
                .sameSite("Lax");
        if (delete) {
            builder.maxAge(0);
        }
        return builder.build();
    }
}
