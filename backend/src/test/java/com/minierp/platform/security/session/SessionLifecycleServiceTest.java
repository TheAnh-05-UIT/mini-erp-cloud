package com.minierp.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

/** Tests deterministic local session-lifecycle rules without waiting. / Kiểm tra vòng đời session local xác định, không cần chờ thời gian thực. */
class SessionLifecycleServiceTest {

    @Test
    void expiresAtTheExactEightHourAbsoluteBoundary() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-20T00:00:00Z"));
        SessionLifecycleService service = service(clock, 2);
        UUID userId = UUID.randomUUID();
        MockHttpSession session = register(service, userId);

        clock.advance(Duration.ofHours(8));

        assertThat(service.isValid(userId, session)).isFalse();
    }

    @Test
    void keepsTheConfiguredThirtyMinuteIdleTimeoutOnNewSessions() {
        SessionLifecycleService service = service(new MutableClock(Instant.EPOCH), 2);
        MockHttpSession session = register(service, UUID.randomUUID());

        assertThat(session.getMaxInactiveInterval()).isEqualTo(1800);
    }

    @Test
    void thirdLoginExpiresTheOldestSession() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        SessionLifecycleService service = service(clock, 2);
        UUID userId = UUID.randomUUID();
        MockHttpSession oldest = register(service, userId);
        clock.advance(Duration.ofSeconds(1));
        MockHttpSession second = register(service, userId);
        clock.advance(Duration.ofSeconds(1));
        MockHttpSession newest = register(service, userId);

        assertThat(oldest.isInvalid()).isTrue();
        assertThat(second.isInvalid()).isFalse();
        assertThat(newest.isInvalid()).isFalse();
        assertThat(service.activeSessionCount(userId)).isEqualTo(2);
    }

    @Test
    void concurrentLoginsNeverLeaveMoreThanTwoRegisteredSessions() throws Exception {
        SessionLifecycleService service = service(new MutableClock(Instant.EPOCH), 2);
        UUID userId = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        List<MockHttpSession> sessions = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            for (int index = 0; index < 3; index++) {
                executor.submit(() -> {
                    MockHttpSession session = new MockHttpSession();
                    synchronized (sessions) {
                        sessions.add(session);
                    }
                    ready.countDown();
                    start.await();
                    MockHttpServletRequest request = new MockHttpServletRequest();
                    request.setSession(session);
                    service.registerAuthenticatedSession(userId, request);
                    return null;
                });
            }
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(service.activeSessionCount(userId)).isEqualTo(2);
        assertThat(sessions.stream().filter(session -> service.isValid(userId, session)).count()).isEqualTo(2);
    }

    @Test
    void logoutInvalidationRemovesTheRegistryEntry() {
        SessionLifecycleService service = service(new MutableClock(Instant.EPOCH), 2);
        UUID userId = UUID.randomUUID();
        MockHttpSession session = register(service, userId);

        service.invalidate(session);

        assertThat(session.isInvalid()).isTrue();
        assertThat(service.activeSessionCount(userId)).isZero();
    }

    private SessionLifecycleService service(Clock clock, int maximumConcurrentSessions) {
        SessionLifecycleProperties properties = new SessionLifecycleProperties();
        properties.setMaximumConcurrentSessions(maximumConcurrentSessions);
        properties.setAbsoluteTimeout(Duration.ofHours(8));
        return new SessionLifecycleService(properties, clock);
    }

    private MockHttpSession register(SessionLifecycleService service, UUID userId) {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        service.registerAuthenticatedSession(userId, request);
        return session;
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}
