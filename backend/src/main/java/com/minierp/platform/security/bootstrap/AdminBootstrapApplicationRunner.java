package com.minierp.platform.security.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Runs after Spring Boot has initialized Flyway and the application context. */
/**
 * Invokes the guarded first-Admin bootstrap only after application startup. / Gọi bootstrap Admin đầu tiên có kiểm soát sau khi ứng dụng khởi động.
 */
@Component
class AdminBootstrapApplicationRunner implements ApplicationRunner {

    private final AdminBootstrapService adminBootstrapService;

    AdminBootstrapApplicationRunner(AdminBootstrapService adminBootstrapService) {
        this.adminBootstrapService = adminBootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        adminBootstrapService.bootstrapIfEnabled();
    }
}
