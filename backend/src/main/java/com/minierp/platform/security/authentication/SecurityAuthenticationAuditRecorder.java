package com.minierp.platform.security.authentication;

import com.minierp.platform.security.persistence.UserAccount;

/**
 * Defines authentication-audit writes independently of credential verification. / Tách thao tác ghi audit xác thực khỏi việc kiểm tra thông tin đăng nhập.
 */
public interface SecurityAuthenticationAuditRecorder {

    void recordSuccessfulLogin(UserAccount user);

    void recordFailedLogin(String normalizedUsername);
}
