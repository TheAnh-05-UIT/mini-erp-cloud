package com.minierp.platform.security.web;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.minierp.platform.security.useradmin.DuplicateUsernameException;
import com.minierp.platform.security.useradmin.LastActiveAdminConflictException;
import com.minierp.platform.security.useradmin.UserNotFoundException;

/** Maps User Administration failures to approved safe responses. / Anh xa loi Quan tri nguoi dung thanh response an toan da phe duyet. */
@RestControllerAdvice(basePackageClasses = UserAdministrationController.class)
class UserAdministrationApiExceptionHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<ApiErrorResponse> invalidRequest() { return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is invalid."); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> accessDenied() { return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied."); }
    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound() { return error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."); }
    @ExceptionHandler(DuplicateUsernameException.class)
    ResponseEntity<ApiErrorResponse> duplicate() { return error(HttpStatus.CONFLICT, "DUPLICATE_USERNAME", "The username is already in use."); }
    @ExceptionHandler(LastActiveAdminConflictException.class)
    ResponseEntity<ApiErrorResponse> lastAdmin() { return error(HttpStatus.CONFLICT, "LAST_ACTIVE_ADMIN", "The last active Admin cannot be changed."); }
    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) { return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, status.value(), Instant.now())); }
}
