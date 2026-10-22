package com.minierp.platform.catalog.web;

import java.time.Instant;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.minierp.platform.catalog.application.*;
import com.minierp.platform.security.web.ApiErrorResponse;

/**
 * Maps catalog failures to safe API errors. / Anh xa loi catalog thanh API an
 * toan.
 */
@RestControllerAdvice(basePackageClasses = CatalogController.class)
class CatalogApiExceptionHandler {
    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, status.value(), Instant.now()));
    }

    @ExceptionHandler({ MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            IllegalArgumentException.class })
    ResponseEntity<ApiErrorResponse> invalid() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is invalid.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> denied() {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied.");
    }

    @ExceptionHandler(CatalogNotFoundException.class)
    ResponseEntity<ApiErrorResponse> missing() {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Catalog resource not found.");
    }

    @ExceptionHandler(CatalogConflictException.class)
    ResponseEntity<ApiErrorResponse> conflict(CatalogConflictException exception) {
        return error(HttpStatus.CONFLICT, exception.code(), "The catalog state conflicts with this request.");
    }
}
