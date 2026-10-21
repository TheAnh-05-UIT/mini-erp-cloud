package com.minierp.platform.security.web;

import jakarta.validation.constraints.NotBlank;

/** Login credentials accepted only over the protected request body. / Thông tin đăng nhập chỉ được nhận trong request body được bảo vệ. */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}
