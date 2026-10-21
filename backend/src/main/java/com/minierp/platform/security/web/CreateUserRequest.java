package com.minierp.platform.security.web;

import java.util.Set;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import com.minierp.platform.security.persistence.RoleCode;

/** Accepts only approved fields for a new user. / Chi nhan truong da phe duyet khi tao user. */
public record CreateUserRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 200) String displayName,
        @NotBlank @Size(min = 12, max = 128) String initialPassword,
        @NotEmpty Set<RoleCode> roleCodes) { }
