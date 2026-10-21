package com.minierp.platform.security.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Allows only approved profile updates. / Chi cho phep cap nhat ho so da phe duyet. */
public record UpdateUserRequest(@NotBlank @Size(max = 200) String displayName) { }
