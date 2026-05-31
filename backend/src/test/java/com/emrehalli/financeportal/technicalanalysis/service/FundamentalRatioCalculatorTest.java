package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.technicalanalysis.entity.CompanyFinancials;
import com.emrehalli.financeportal.technicalanalysis.entity.FundamentalRatios;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calculator'ın hiçbir repository/service bağımlılığı olmadan, saf girdilerle çalıştığını ve
 * golden değerleri ürettiğini doğrular. (Servis üzerinden geçen golden testler de ayrıca mevcut.)
 */
class FundamentalRatioCalculatorTest {

    private final FundamentalRatioCalculator calculator = new FundamentalRatioCalculator();

    @Test
    void populate_should_compute_golden_values_for_complete_single_year() {
        CompanyFinancials f = fullFinancials().build();
        FundamentalRatios ratios = FundamentalRatios.builder().build();

        calculator.populate(ratios, f, List.of(f), new BigDecimal("150.00"), new BigDecimal("1000000"));

        assertThat(ratios.getGrossMargin()).isEqualTo(new BigDecimal("30.0000"));
        assertThat(ratios.getRoe()).isEqualTo(new BigDecimal("15.0000"));
        assertThat(ratios.getRoa()).isEqualTo(new BigDecimal("7.5000"));
        assertThat(ratios.getCurrentRatio()).isEqualTo(new BigDecimal("1.6667"));
        assertThat(ratios.getPeRatio()).isEqualTo(new BigDecimal("10.0000"));
        assertThat(ratios.getPbRatio()).isEqualTo(new BigDecimal("1.5000"));
        assertThat(ratios.getGrahamNumber()).isEqualTo(new BigDecimal("183.7117"));
        assertThat(ratios.getAltmanZScore()).isEqualTo(new BigDecimal("1.7675"));
        assertThat(ratios.getOverallSignal()).isEqualTo("NEUTRAL");
        assertThat(ratios.getPiotroskiScore()).isNull();
    }

    @Test
    void populate_should_skip_per_share_ratios_when_shares_null() {
        CompanyFinancials f = fullFinancials().build();
        FundamentalRatios ratios = FundamentalRatios.builder().build();

        calculator.populate(ratios, f, List.of(f), new BigDecimal("150.00"), null);

        assertThat(ratios.getPeRatio()).isNull();
        assertThat(ratios.getPbRatio()).isNull();
        assertThat(ratios.getGrahamNumber()).isNull();
        assertThat(ratios.getAltmanZScore()).isNull();
        // Hisse adedinden bağımsız oranlar korunur.
        assertThat(ratios.getRoe()).isEqualTo(new BigDecimal("15.0000"));
        assertThat(ratios.getNetMargin()).isEqualTo(new BigDecimal("15.0000"));
    }

    private CompanyFinancials.CompanyFinancialsBuilder fullFinancials() {
        return CompanyFinancials.builder()
                .period("2024/Y")
                .periodType("ANNUAL")
                .revenue(new BigDecimal("100000000"))
                .grossProfit(new BigDecimal("30000000"))
                .netIncome(new BigDecimal("15000000"))
                .totalAssets(new BigDecimal("200000000"))
                .totalEquity(new BigDecimal("100000000"))
                .totalLiabilities(new BigDecimal("100000000"))
                .currentAssets(new BigDecimal("50000000"))
                .currentLiabilities(new BigDecimal("30000000"))
                .operatingCashFlow(new BigDecimal("20000000"));
    }
}
