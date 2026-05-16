package com.emrehalli.financeportal.ai.access;

/**
 * Catalog of all AI features with their access tier.
 * Free features are available to all authenticated (and in some cases unauthenticated) users.
 * Premium features require the USER_PREMIUM or ADMIN role.
 */
public enum AiFeatureType {

    // ── Free tier ─────────────────────────────────────────────────
    BASIC_CHAT(false),
    TECHNICAL_ANALYSIS(false),
    FUNDAMENTAL_ANALYSIS(false),
    NEWS_SUMMARY(false),

    // ── Premium tier ──────────────────────────────────────────────
    UNIFIED_ANALYSIS(true),
    NEWS_IMPACT_ANALYSIS(true),
    PORTFOLIO_AI(true);

    private final boolean premiumRequired;

    AiFeatureType(boolean premiumRequired) {
        this.premiumRequired = premiumRequired;
    }

    public boolean isPremiumRequired() {
        return premiumRequired;
    }
}
