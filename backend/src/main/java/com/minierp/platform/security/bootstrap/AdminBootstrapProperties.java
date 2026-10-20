package com.minierp.platform.security.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds externally supplied bootstrap settings; no credentials are kept in source.
 * Ánh xạ cấu hình bootstrap từ bên ngoài; không lưu credentials trong source.
 */
@ConfigurationProperties(prefix = "mini-erp.bootstrap.admin")
public class AdminBootstrapProperties {

    private boolean enabled;
    private String username;
    private String password;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
