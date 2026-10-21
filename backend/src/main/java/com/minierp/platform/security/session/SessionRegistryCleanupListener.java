package com.minierp.platform.security.session;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

/** Removes container-destroyed sessions from the local registry. / Xóa session bị container hủy khỏi registry local. */
public class SessionRegistryCleanupListener implements HttpSessionListener {

    private final SessionLifecycleService sessionLifecycleService;

    public SessionRegistryCleanupListener(SessionLifecycleService sessionLifecycleService) {
        this.sessionLifecycleService = sessionLifecycleService;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        sessionLifecycleService.forget(event.getSession());
    }
}
