package com.emrehalli.financeportal.ai.core.provider;

import java.util.Optional;

public interface LlmClient {
    Optional<String> generate(String prompt);
    String providerName();
    boolean isConfigured();
}




