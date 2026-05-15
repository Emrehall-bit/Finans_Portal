package com.emrehalli.financeportal.ai.provider;

public class AiProviderException extends RuntimeException {

    private final AiProviderType providerType;

    public AiProviderException(AiProviderType providerType, String message) {
        super(message);
        this.providerType = providerType;
    }

    public AiProviderType getProviderType() {
        return providerType;
    }
}
