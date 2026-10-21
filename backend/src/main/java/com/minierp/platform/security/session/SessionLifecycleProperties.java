package com.minierp.platform.security.session;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Defines local server-session lifetime limits. / Xác định giới hạn vòng đời server session local. */
@ConfigurationProperties(prefix = "mini-erp.security.session")
public class SessionLifecycleProperties {

    private Duration idleTimeout = Duration.ofMinutes(30);
    private Duration absoluteTimeout = Duration.ofHours(8);
    private int maximumConcurrentSessions = 2;

    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(Duration idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public Duration getAbsoluteTimeout() {
        return absoluteTimeout;
    }

    public void setAbsoluteTimeout(Duration absoluteTimeout) {
        this.absoluteTimeout = absoluteTimeout;
    }

    public int getMaximumConcurrentSessions() {
        return maximumConcurrentSessions;
    }

    public void setMaximumConcurrentSessions(int maximumConcurrentSessions) {
        this.maximumConcurrentSessions = maximumConcurrentSessions;
    }
}
