package com.minierp.platform.persistence;

import java.net.URI;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Fails closed unless concurrency tests use their dedicated database and role. / Từ chối chạy nếu test concurrency không dùng đúng database và role riêng. */
public final class ConcurrencyTestDatabaseSafetyInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override public void initialize(ConfigurableApplicationContext context) {
        var env = context.getEnvironment();
        String url = env.getRequiredProperty("spring.datasource.url");
        String user = env.getRequiredProperty("spring.datasource.username");
        if (!"/mini_erp_concurrency_test".equals(URI.create(url.substring("jdbc:".length())).getPath()) || !"mini_erp_concurrency_app".equals(user))
            throw new IllegalStateException("Concurrency tests require mini_erp_concurrency_test as mini_erp_concurrency_app.");
    }
}
