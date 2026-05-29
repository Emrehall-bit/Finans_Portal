package com.emrehalli.financeportal.ai.access;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiFeatureTypeTest {

    @Test
    void freeFeaturesRemainAccessibleToNormalUsers() {
        assertThat(AiFeatureType.BASIC_CHAT.isPremiumRequired()).isFalse();
        assertThat(AiFeatureType.TECHNICAL_ANALYSIS.isPremiumRequired()).isFalse();
        assertThat(AiFeatureType.FUNDAMENTAL_ANALYSIS.isPremiumRequired()).isFalse();
        assertThat(AiFeatureType.NEWS_SUMMARY.isPremiumRequired()).isFalse();
    }

    @Test
    void premiumFeaturesRequirePremiumOrAdmin() {
        assertThat(AiFeatureType.UNIFIED_ANALYSIS.isPremiumRequired()).isTrue();
        assertThat(AiFeatureType.NEWS_IMPACT_ANALYSIS.isPremiumRequired()).isTrue();
        assertThat(AiFeatureType.COMPANY_COMPARISON_AI.isPremiumRequired()).isTrue();
        assertThat(AiFeatureType.PORTFOLIO_AI.isPremiumRequired()).isTrue();
    }
}




