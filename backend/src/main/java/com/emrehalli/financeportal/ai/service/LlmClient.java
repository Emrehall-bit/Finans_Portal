package com.emrehalli.financeportal.ai.service;

import java.util.Optional;

public interface LlmClient {
    Optional<String> generate(String prompt);
    String providerName();
    boolean isConfigured();
}



