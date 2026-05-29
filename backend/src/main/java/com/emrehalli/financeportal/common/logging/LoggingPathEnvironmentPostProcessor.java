package com.emrehalli.financeportal.common.logging;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class LoggingPathEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "financePortalLoggingPath";
    private static final String APP_LOGGING_PATH = "app.logging.path";
    private static final String LOG_PATH = "LOG_PATH";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String explicitLogPath = firstNonBlank(
                environment.getProperty(LOG_PATH),
                System.getProperty(APP_LOGGING_PATH)
        );

        String logPath = explicitLogPath != null ? explicitLogPath : resolveDefaultLogPath();
        environment.getPropertySources().addFirst(
                new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(APP_LOGGING_PATH, logPath))
        );
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    private static String resolveDefaultLogPath() {
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if ("backend".equalsIgnoreCase(workingDirectory.getFileName().toString())) {
            return workingDirectory.resolve("logs").toString();
        }

        Path backendDirectory = workingDirectory.resolve("backend");
        if (Files.isDirectory(backendDirectory)) {
            return backendDirectory.resolve("logs").toString();
        }

        return workingDirectory.resolve("logs").toString();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}




