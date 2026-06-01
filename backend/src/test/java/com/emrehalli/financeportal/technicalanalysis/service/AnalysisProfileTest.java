package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisProfileTest {

    @Test
    void forType_null_returns_default_profile() {
        assertThat(AnalysisProfile.forType(null)).isEqualTo(AnalysisProfile.DEFAULT);
    }

    @Test
    void forType_stock_returns_default_profile() {
        assertThat(AnalysisProfile.forType(InstrumentType.STOCK)).isEqualTo(AnalysisProfile.DEFAULT);
    }

    @Test
    void forType_futures_returns_default_profile() {
        assertThat(AnalysisProfile.forType(InstrumentType.FUTURES)).isEqualTo(AnalysisProfile.DEFAULT);
    }

    @Test
    void forType_bond_returns_default_profile() {
        assertThat(AnalysisProfile.forType(InstrumentType.BOND)).isEqualTo(AnalysisProfile.DEFAULT);
    }

    @Test
    void default_profile_matches_original_hardcoded_values() {
        AnalysisProfile p = AnalysisProfile.DEFAULT;
        assertThat(p.shortTermMomentumThresholdPct()).isEqualByComparingTo("0.10");
        assertThat(p.rangeTrendThresholdPct()).isEqualByComparingTo("1.0");
        assertThat(p.rsiOverboughtLevel()).isEqualByComparingTo("70");
        assertThat(p.rsiOversoldLevel()).isEqualByComparingTo("30");
    }

    @Test
    void fx_profile_has_lower_momentum_threshold_and_tighter_range_threshold() {
        AnalysisProfile p = AnalysisProfile.forType(InstrumentType.FX);
        assertThat(p.shortTermMomentumThresholdPct()).isEqualByComparingTo("0.05");
        assertThat(p.rangeTrendThresholdPct()).isEqualByComparingTo("0.5");
        assertThat(p.rsiOverboughtLevel()).isEqualByComparingTo("70");
        assertThat(p.rsiOversoldLevel()).isEqualByComparingTo("30");
    }

    @Test
    void crypto_profile_has_higher_momentum_threshold_and_wider_rsi_bands() {
        AnalysisProfile p = AnalysisProfile.forType(InstrumentType.CRYPTO);
        assertThat(p.shortTermMomentumThresholdPct()).isEqualByComparingTo("0.50");
        assertThat(p.rangeTrendThresholdPct()).isEqualByComparingTo("3.0");
        assertThat(p.rsiOverboughtLevel()).isEqualByComparingTo("75");
        assertThat(p.rsiOversoldLevel()).isEqualByComparingTo("25");
    }

    @Test
    void fund_profile_has_conservative_rsi_bands() {
        AnalysisProfile p = AnalysisProfile.forType(InstrumentType.FUND);
        assertThat(p.shortTermMomentumThresholdPct()).isEqualByComparingTo("0.10");
        assertThat(p.rangeTrendThresholdPct()).isEqualByComparingTo("0.5");
        assertThat(p.rsiOverboughtLevel()).isEqualByComparingTo("65");
        assertThat(p.rsiOversoldLevel()).isEqualByComparingTo("35");
    }

    @Test
    void fx_threshold_is_lower_than_default() {
        assertThat(AnalysisProfile.forType(InstrumentType.FX).shortTermMomentumThresholdPct())
                .isLessThan(AnalysisProfile.DEFAULT.shortTermMomentumThresholdPct());
    }

    @Test
    void crypto_threshold_is_higher_than_default() {
        assertThat(AnalysisProfile.forType(InstrumentType.CRYPTO).shortTermMomentumThresholdPct())
                .isGreaterThan(AnalysisProfile.DEFAULT.shortTermMomentumThresholdPct());
    }
}
