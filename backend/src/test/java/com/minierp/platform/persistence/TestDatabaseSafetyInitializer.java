package com.minierp.platform.persistence;

import java.net.URI;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class TestDatabaseSafetyInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String EXPECTED_DATABASE_PATH = "/mini_erp_test";
    private static final String EXPECTED_USERNAME = "mini_erp_test_app";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        String datasourceUrl = applicationContext.getEnvironment()
                .getRequiredProperty("spring.datasource.url");
        String datasourceUsername = applicationContext.getEnvironment()
                .getRequiredProperty("spring.datasource.username");

        if (!usesTestDatabase(datasourceUrl) || !EXPECTED_USERNAME.equals(datasourceUsername)) {
            throw new IllegalStateException("Database integration tests require the isolated mini_erp_test datasource.");
        }
    }

    private boolean usesTestDatabase(String datasourceUrl) {
        if (!datasourceUrl.startsWith("jdbc:")) {
            return false;
        }

        return EXPECTED_DATABASE_PATH.equals(URI.create(datasourceUrl.substring("jdbc:".length())).getPath());
    }
}
