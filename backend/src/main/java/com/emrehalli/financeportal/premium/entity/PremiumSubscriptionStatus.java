package com.emrehalli.financeportal.premium.entity;

public enum PremiumSubscriptionStatus {
    PREMIUM_REQUESTED,
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    ACTIVE,
    CANCELLED;

    public boolean isTerminal() {
        return this == PAYMENT_FAILED || this == ACTIVE || this == CANCELLED;
    }

    public boolean isPending() {
        return this == PREMIUM_REQUESTED || this == PAYMENT_PENDING;
    }
}
