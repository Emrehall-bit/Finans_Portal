package com.emrehalli.financeportal.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;

/**
 * Restricts Spring's entity scanning to the application package only.
 * jBPM runs in in-memory mode (newEmptyBuilder) so its JPA entity classes
 * must NOT be included — they are incompatible with Hibernate 6.
 */
@Configuration
@EntityScan(basePackages = "com.emrehalli.financeportal")
public class JbpmConfig {
}
