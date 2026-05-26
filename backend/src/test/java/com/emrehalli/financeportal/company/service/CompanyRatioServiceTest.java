package com.emrehalli.financeportal.company.service;

import com.emrehalli.financeportal.company.dto.response.CompanyRatioCalculationResponse;
import com.emrehalli.financeportal.company.domain.entity.*;
import com.emrehalli.financeportal.company.domain.enums.ParseStatus;
import com.emrehalli.financeportal.company.domain.enums.ReportType;
import com.emrehalli.financeportal.company.domain.enums.FinancialItemKey;
import com.emrehalli.financeportal.company.persistence.*;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompanyRatioServiceTest {

    private CompanyProfileRepository profileRepository;
    private CompanyFinancialReportRepository reportRepository;
    private CompanyFinancialValueRepository valueRepository;
    private CompanyRatioRepository ratioRepository;
    private MarketQueryService marketQueryService;
    private FinancialQuarterNormalizer quarterNormalizer;

    private CompanyRatioService service;

    private CompanyProfile company;
    private CompanyFinancialReport report;

    @BeforeEach
    void setUp() {
        profileRepository    = mock(CompanyProfileRepository.class);
        reportRepository     = mock(CompanyFinancialReportRepository.class);
        valueRepository      = mock(CompanyFinancialValueRepository.class);
        ratioRepository      = mock(CompanyRatioRepository.class);
        marketQueryService   = mock(MarketQueryService.class);
        quarterNormalizer    = mock(FinancialQuarterNormalizer.class);

        service = new CompanyRatioService(profileRepository, reportRepository,
                valueRepository, ratioRepository, marketQueryService, quarterNormalizer);

        company = CompanyProfile.builder()
                .id(1L).tickerCode("TEST").companyName("Test A.Ş.")
                .sector("Sektör").market("BIST").build();

        company.setSharesOutstanding(new BigDecimal("100000"));

        report = CompanyFinancialReport.builder()
                .id(10L).company(company)
                .periodYear(2024).periodQuarter(4).reportType(ReportType.ANNUAL)
                .parseStatus(ParseStatus.SUCCESS)
                .build();

        when(profileRepository.findByTickerCodeIgnoreCase("TEST")).thenReturn(Optional.of(company));
        when(reportRepository.findEligibleReports(eq("TEST"), anyCollection())).thenReturn(List.of(report));
        when(ratioRepository.findByCompanyIdAndReportId(1L, 10L)).thenReturn(Optional.empty());
        when(ratioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(quarterNormalizer.effectiveValue(any(CompanyFinancialValue.class)))
                .thenAnswer(inv -> ((CompanyFinancialValue) inv.getArgument(0)).getValue());
    }

    // -------------------------------------------------------------------------
    // peRatio — null when net profit is negative or zero
    // -------------------------------------------------------------------------

    @Test
    void peRatio_isNegative_whenNetProfitIsNegative() {
        stubMarketPrice("100");
        stubValues(List.of(
                value(FinancialItemKey.HASILAT, "1000000"),
                value(FinancialItemKey.BRUT_KAR, "300000"),
                value(FinancialItemKey.NET_DONEM_KARI, "-50000"),   // negative
                value(FinancialItemKey.OZKAYNAKLAR, "500000"),
                value(FinancialItemKey.TOPLAM_VARLIKLAR, "800000"),
                value(FinancialItemKey.TOPLAM_YUKUMLULUKLER, "300000"),
                value(FinancialItemKey.ODENMIS_SERMAYE, "100000")
        ));
        when(quarterNormalizer.getQuarterlyValue(1L, FinancialItemKey.NET_DONEM_KARI, 2024, 4))
                .thenReturn(new BigDecimal("-50000"));
        when(quarterNormalizer.getQuarterlyValue(1L, FinancialItemKey.HASILAT, 2024, 4))
                .thenReturn(new BigDecimal("1000000"));

        CompanyRatioCalculationResponse result = service.calculateForTicker("TEST");

        assertThat(result.isCalculated()).isTrue();
        assertThat(result.getPeRatio()).isEqualByComparingTo("-200.000000");
        assertThat(result.getPeStatus()).isEqualTo("NEGATIVE_EARNINGS");
        assertThat(result.getPeMissingReason()).isEqualTo("negative earnings");
        assertThat(result.getNetMargin()).isNotNull().isNegative();
    }

    @Test
    void peRatio_isNull_whenNetProfitIsZero() {
        stubMarketPrice("100");
        stubValues(List.of(
                value(FinancialItemKey.HASILAT, "1000000"),
                value(FinancialItemKey.BRUT_KAR, "200000"),
                value(FinancialItemKey.NET_DONEM_KARI, "0"),         // zero
                value(FinancialItemKey.OZKAYNAKLAR, "500000"),
                value(FinancialItemKey.TOPLAM_VARLIKLAR, "800000"),
                value(FinancialItemKey.TOPLAM_YUKUMLULUKLER, "300000"),
                value(FinancialItemKey.ODENMIS_SERMAYE, "100000")
        ));

        CompanyRatioCalculationResponse result = service.calculateForTicker("TEST");

        assertThat(result.getPeRatio()).isNull();
        assertThat(result.getPeStatus()).isEqualTo("ZERO_OR_MISSING_EARNINGS");
        assertThat(result.getPeMissingReason()).isEqualTo("zero or missing earnings");
    }

    // -------------------------------------------------------------------------
    // Zero denominator — ratio must be null, no exception
    // -------------------------------------------------------------------------

    @Test
    void ratios_areNull_whenDenominatorsAreZero() {
        stubMarketPrice("100");
        stubValues(List.of(
                value(FinancialItemKey.HASILAT, "0"),                // zero → margins null
                value(FinancialItemKey.BRUT_KAR, "200000"),
                value(FinancialItemKey.NET_DONEM_KARI, "50000"),
                value(FinancialItemKey.OZKAYNAKLAR, "0"),            // zero → pbRatio, roe, debtToEquity null
                value(FinancialItemKey.TOPLAM_VARLIKLAR, "0"),       // zero → roa null
                value(FinancialItemKey.TOPLAM_YUKUMLULUKLER, "300000"),
                value(FinancialItemKey.ODENMIS_SERMAYE, "100000")
        ));

        CompanyRatioCalculationResponse result = service.calculateForTicker("TEST");

        assertThat(result.isCalculated()).isTrue();
        assertThat(result.getPbRatio()).isNull();
        assertThat(result.getPbStatus()).isEqualTo("ZERO_EQUITY");
        assertThat(result.getPbMissingReason()).isEqualTo("zero equity");
        assertThat(result.getRoe()).isNull();
        assertThat(result.getRoa()).isNull();
        assertThat(result.getDebtToEquity()).isNull();
        assertThat(result.getGrossMargin()).isNull();
        assertThat(result.getNetMargin()).isNull();
    }

    // -------------------------------------------------------------------------
    // Growth rates — null when no previous period
    // -------------------------------------------------------------------------

    @Test
    void growthRates_areNull_whenNoPreviousPeriod() {
        // Only one eligible report — no previous period
        when(reportRepository.findEligibleReports(eq("TEST"), anyCollection())).thenReturn(List.of(report));
        stubMarketPrice("100");
        stubValues(List.of(
                value(FinancialItemKey.HASILAT, "1000000"),
                value(FinancialItemKey.BRUT_KAR, "300000"),
                value(FinancialItemKey.NET_DONEM_KARI, "100000"),
                value(FinancialItemKey.OZKAYNAKLAR, "500000"),
                value(FinancialItemKey.TOPLAM_VARLIKLAR, "800000"),
                value(FinancialItemKey.TOPLAM_YUKUMLULUKLER, "300000"),
                value(FinancialItemKey.ODENMIS_SERMAYE, "100000")
        ));

        CompanyRatioCalculationResponse result = service.calculateForTicker("TEST");

        assertThat(result.isCalculated()).isTrue();
        assertThat(result.getRevenueGrowth()).isNull();
        assertThat(result.getNetProfitGrowth()).isNull();
        assertThat(result.getAssetGrowth()).isNull();
    }

    @Test
    void pbRatio_isCalculated_whenEquityExists_evenIfGrossMarginInputsAreMissing() {
        stubMarketPrice("100");
        stubValues(List.of(
                value(FinancialItemKey.HASILAT, "1000000"),
                value(FinancialItemKey.NET_DONEM_KARI, "100000"),
                value(FinancialItemKey.OZKAYNAKLAR, "500000"),
                value(FinancialItemKey.TOPLAM_VARLIKLAR, "800000"),
                value(FinancialItemKey.TOPLAM_YUKUMLULUKLER, "300000")
        ));
        when(quarterNormalizer.getQuarterlyValue(1L, FinancialItemKey.HASILAT, 2024, 4))
                .thenReturn(new BigDecimal("1000000"));
        when(quarterNormalizer.getQuarterlyValue(1L, FinancialItemKey.BRUT_KAR, 2024, 4))
                .thenReturn(null);
        when(quarterNormalizer.getQuarterlyValue(1L, FinancialItemKey.NET_DONEM_KARI, 2024, 4))
                .thenReturn(new BigDecimal("100000"));
        when(quarterNormalizer.getTtmValue(1L, FinancialItemKey.NET_DONEM_KARI, 2024, 4))
                .thenReturn(new BigDecimal("100000"));

        CompanyRatioCalculationResponse result = service.calculateForTicker("TEST");

        assertThat(result.isCalculated()).isTrue();
        assertThat(result.getGrossMargin()).isNull();
        assertThat(result.getPbRatio()).isEqualByComparingTo("20.000000");
        assertThat(result.getPbStatus()).isEqualTo("CALCULATED");
        assertThat(result.getPbMissingReason()).isNull();
    }

    @Test
    void peAndPbStatuses_reportMissingSharesOutstanding() {
        company.setSharesOutstanding(null);
        stubMarketPrice("100");
        stubValues(List.of(
                value(FinancialItemKey.HASILAT, "1000000"),
                value(FinancialItemKey.NET_DONEM_KARI, "100000"),
                value(FinancialItemKey.OZKAYNAKLAR, "500000"),
                value(FinancialItemKey.TOPLAM_VARLIKLAR, "800000"),
                value(FinancialItemKey.TOPLAM_YUKUMLULUKLER, "300000")
        ));
        when(quarterNormalizer.getQuarterlyValue(1L, FinancialItemKey.HASILAT, 2024, 4))
                .thenReturn(new BigDecimal("1000000"));
        when(quarterNormalizer.getQuarterlyValue(1L, FinancialItemKey.NET_DONEM_KARI, 2024, 4))
                .thenReturn(new BigDecimal("100000"));
        when(quarterNormalizer.getTtmValue(1L, FinancialItemKey.NET_DONEM_KARI, 2024, 4))
                .thenReturn(new BigDecimal("100000"));

        CompanyRatioCalculationResponse result = service.calculateForTicker("TEST");

        assertThat(result.isCalculated()).isTrue();
        assertThat(result.getPeRatio()).isNull();
        assertThat(result.getPbRatio()).isNull();
        assertThat(result.getPeStatus()).isEqualTo("MISSING_SHARES_OUTSTANDING");
        assertThat(result.getPbStatus()).isEqualTo("MISSING_SHARES_OUTSTANDING");
        assertThat(result.getMissingReason()).contains("pe: missing shares outstanding");
        assertThat(result.getMissingReason()).contains("pb: missing shares outstanding");
    }

    // -------------------------------------------------------------------------
    // Direct unit tests for arithmetic helpers
    // -------------------------------------------------------------------------

    @Test
    void divide_returnsNull_whenDenominatorIsZero() {
        assertThat(service.divide(new BigDecimal("100"), BigDecimal.ZERO)).isNull();
    }

    @Test
    void divide_returnsNull_whenNumeratorIsNull() {
        assertThat(service.divide(null, new BigDecimal("100"))).isNull();
    }

    @Test
    void divide_returnsNull_whenDenominatorIsNull() {
        assertThat(service.divide(new BigDecimal("100"), null)).isNull();
    }

    @Test
    void growth_returnsNull_whenPreviousIsZero() {
        assertThat(service.growth(new BigDecimal("100"), BigDecimal.ZERO)).isNull();
    }

    @Test
    void growth_returnsNull_whenPreviousIsNull() {
        assertThat(service.growth(new BigDecimal("100"), null)).isNull();
    }

    @Test
    void healthLabel_debtHigh_whenDebtToEquityAbove2() {
        String label = service.computeHealthLabel(
                new BigDecimal("2.5"),
                new BigDecimal("0.20"),
                new BigDecimal("0.20"));
        assertThat(label).isEqualTo("Borçluluk yüksek");
    }

    @Test
    void healthLabel_profitable_whenMarginAndRoeAbove15pct() {
        String label = service.computeHealthLabel(
                new BigDecimal("1.0"),
                new BigDecimal("0.20"),
                new BigDecimal("0.20"));
        assertThat(label).isEqualTo("Kârlılık güçlü");
    }

    @Test
    void healthLabel_loss_whenNetMarginNegative() {
        String label = service.computeHealthLabel(
                new BigDecimal("1.0"),
                new BigDecimal("-0.05"),
                new BigDecimal("0.10"));
        assertThat(label).isEqualTo("Zarar açıklamış");
    }

    @Test
    void healthLabel_neutral_whenNoSpecialCondition() {
        String label = service.computeHealthLabel(
                new BigDecimal("1.0"),
                new BigDecimal("0.05"),
                new BigDecimal("0.08"));
        assertThat(label).isEqualTo("Nötr");
    }

    // -------------------------------------------------------------------------
    // Failure cases
    // -------------------------------------------------------------------------

    @Test
    void calculateForTicker_fails_whenNoEligibleReport() {
        when(reportRepository.findEligibleReports(eq("TEST"), anyCollection())).thenReturn(List.of());
        stubMarketPrice("100");

        CompanyRatioCalculationResponse result = service.calculateForTicker("TEST");

        assertThat(result.isCalculated()).isFalse();
        assertThat(result.getFailedReason()).isNotBlank();
    }

    @Test
    void calculateForTicker_fails_whenPriceNotFound() {
        when(marketQueryService.findBySymbol("TEST")).thenReturn(Optional.empty());

        CompanyRatioCalculationResponse result = service.calculateForTicker("TEST");

        assertThat(result.isCalculated()).isFalse();
        assertThat(result.getFailedReason()).contains("fiyat");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void stubMarketPrice(String price) {
        when(marketQueryService.findBySymbol("TEST")).thenReturn(Optional.of(
                new MarketQueryService.MarketSnapshot(
                        "TEST", "Test A.Ş.", new BigDecimal(price),
                        BigDecimal.ZERO, "BIST", "STOCK", "TRY", LocalDateTime.now())));
    }

    private void stubValues(List<CompanyFinancialValue> vals) {
        when(valueRepository.findByReportId(10L)).thenReturn(vals);
    }

    private static CompanyFinancialValue value(FinancialItemKey key, String val) {
        return CompanyFinancialValue.builder()
                .itemKey(key.name())
                .value(new BigDecimal(val))
                .build();
    }
}



