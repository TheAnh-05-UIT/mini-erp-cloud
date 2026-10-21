package com.minierp.platform.security.session;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Service;

/**
 * Tracks local authenticated sessions and enforces their absolute lifetime.
 * Theo dõi session xác thực local và áp dụng thời hạn tuyệt đối.
 */
@Service
public class SessionLifecycleService {

    static final String ISSUED_AT_ATTRIBUTE = SessionLifecycleService.class.getName() + ".issuedAt";

    private final SessionLifecycleProperties properties;
    private final Clock clock;
    private final Map<UUID, Map<String, SessionRegistration>> sessionsByUser = new HashMap<>();
    private final Map<String, UUID> userBySessionId = new HashMap<>();
    private long registrationSequence;

    public SessionLifecycleService(SessionLifecycleProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Registers a newly authenticated session and expires the oldest excess session. */
    public synchronized void registerAuthenticatedSession(UUID userId, HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        Instant issuedAt = clock.instant();
        session.setMaxInactiveInterval(Math.toIntExact(properties.getIdleTimeout().toSeconds()));
        session.setAttribute(ISSUED_AT_ATTRIBUTE, issuedAt);
        removeBySessionId(session.getId());

        Map<String, SessionRegistration> sessions = sessionsByUser.computeIfAbsent(userId, ignored -> new HashMap<>());
        sessions.put(session.getId(), new SessionRegistration(session, issuedAt, ++registrationSequence));
        userBySessionId.put(session.getId(), userId);
        expireExcessSessions(userId, sessions);
    }

    /** Returns whether the session is still registered for the user and inside its absolute lifetime. */
    public synchronized boolean isValid(UUID userId, HttpSession session) {
        if (session == null || !isRegisteredFor(userId, session.getId())) {
            return false;
        }
        Object issuedAtValue = session.getAttribute(ISSUED_AT_ATTRIBUTE);
        if (!(issuedAtValue instanceof Instant issuedAt)) {
            return false;
        }
        return clock.instant().isBefore(issuedAt.plus(properties.getAbsoluteTimeout()));
    }

    /** Removes tracking and invalidates a live session without logging its identifier. */
    public synchronized void invalidate(HttpSession session) {
        if (session == null) {
            return;
        }
        removeBySessionId(session.getId());
        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // The container already destroyed this session. / Container đã hủy session này.
        }
    }

    /** Cleans registry state when the servlet container destroys a session. */
    public synchronized void forget(HttpSession session) {
        if (session != null) {
            removeBySessionId(session.getId());
        }
    }

    synchronized int activeSessionCount(UUID userId) {
        Map<String, SessionRegistration> sessions = sessionsByUser.get(userId);
        return sessions == null ? 0 : sessions.size();
    }

    private boolean isRegisteredFor(UUID userId, String sessionId) {
        return userId.equals(userBySessionId.get(sessionId));
    }

    private void expireExcessSessions(UUID userId, Map<String, SessionRegistration> sessions) {
        while (sessions.size() > properties.getMaximumConcurrentSessions()) {
            SessionRegistration oldest = sessions.values().stream()
                    .min(Comparator.comparing(SessionRegistration::issuedAt)
                            .thenComparingLong(SessionRegistration::sequence))
                    .orElseThrow();
            removeBySessionId(oldest.session().getId());
            try {
                oldest.session().invalidate();
            } catch (IllegalStateException ignored) {
                // An already-destroyed session is still removed from this registry.
            }
        }
        if (sessions.isEmpty()) {
            sessionsByUser.remove(userId);
        }
    }

    private void removeBySessionId(String sessionId) {
        UUID userId = userBySessionId.remove(sessionId);
        if (userId == null) {
            return;
        }
        Map<String, SessionRegistration> sessions = sessionsByUser.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                sessionsByUser.remove(userId);
            }
        }
    }

    private record SessionRegistration(HttpSession session, Instant issuedAt, long sequence) {
    }
}
