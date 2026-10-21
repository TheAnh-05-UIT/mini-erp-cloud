package com.minierp.platform.security.web;

import java.io.IOException;
import java.time.Instant;

import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Writes framework security failures with the same safe error shape as authentication endpoints. / Ghi lỗi security framework theo cùng error shape an toàn của authentication endpoint. */
@Component
public class SecurityApiErrorWriter {

    private final ObjectMapper objectMapper;

    public SecurityApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(code, message, status, Instant.now()));
    }
}
