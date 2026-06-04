package com.emrehalli.financeportal.ai.core.access;

/**
 * Catalog of all AI features with their access tier.
 * Free features are available to normal authenticated users.
 * Premium features require the USER_PREMIUM or ADMIN role.
 */
public enum AiFeatureType {

    BASIC_CHAT(false),
    TECHNICAL_ANALYSIS(false),
    FUNDAMENTAL_ANALYSIS(false),
    NEWS_SUMMARY(false),

    UNIFIED_ANALYSIS(true),
    NEWS_IMPACT_ANALYSIS(true),
    COMPANY_COMPARISON_AI(true),
    PORTFOLIO_AI(true),
    DASHBOARD_AI(true);

    private final boolean premiumRequired;

    AiFeatureType(boolean premiumRequired) {
        this.premiumRequired = premiumRequired;
    }

    public boolean isPremiumRequired() {
        return premiumRequired;
    }
}




