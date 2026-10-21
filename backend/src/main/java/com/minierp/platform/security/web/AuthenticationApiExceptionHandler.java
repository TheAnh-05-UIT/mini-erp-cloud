package com.minierp.platform.security.web;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.minierp.platform.security.authentication.AuthenticationFailedException;
import com.minierp.platform.security.authentication.SecurityAuditPersistenceException;

/** Converts expected authentication failures into safe API responses. / Chuyển lỗi authentication dự kiến thành API response an toàn. */
@RestControllerAdvice(basePackageClasses = AuthenticationController.class)
class AuthenticationApiExceptionHandler {

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ApiErrorResponse> authenticationFailed() {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication failed.");
    }

    @ExceptionHandler(SecurityAuditPersistenceException.class)
    ResponseEntity<ApiErrorResponse> authenticationAuditUnavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "AUTHENTICATION_UNAVAILABLE", "Authentication is temporarily unavailable.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> malformedRequest() {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request is invalid.");
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(code, message, status.value(), Instant.now()));
    }
}
