package com.minierp.platform.security.authentication;

/** A deliberately generic failure safe for a future login API response. */
/**
 * Represents a deliberately generic rejected login. / Đại diện cho đăng nhập bị từ chối với thông báo chung, tránh lộ thông tin tài khoản.
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        super("Authentication failed");
    }
}
