package com.emrehalli.financeportal.technicalanalysis.fundamental.service;

import com.emrehalli.financeportal.technicalanalysis.fundamental.entity.CompanyFinancials;
import com.emrehalli.financeportal.technicalanalysis.fundamental.entity.FundamentalHistory;
import com.emrehalli.financeportal.technicalanalysis.fundamental.entity.FundamentalRatios;
import com.emrehalli.financeportal.technicalanalysis.fundamental.repository.CompanyFinancialsRepository;
import com.emrehalli.financeportal.technicalanalysis.fundamental.repository.FundamentalHistoryRepository;
import com.emrehalli.financeportal.technicalanalysis.fundamental.repository.FundamentalRatiosRepository;
import com.emrehalli.financeportal.company.domain.entity.CompanyProfile;
import com.emrehalli.financeportal.company.persistence.CompanyProfileRepository;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FundamentalAnalysisServiceTest {

    @Mock CompanyFinancialsRepository companyFinancialsRepository;
    @Mock FundamentalRatiosRepository fundamentalRatiosRepository;
    @Mock FundamentalHistoryRepository fundamentalHistoryRepository;
    @Mock MarketInstrumentRepository marketInstrumentRepository;
    @Mock MarketPriceHistoryRepository marketPriceHistoryRepository;
    @Mock CompanyProfileRepository companyProfileRepository;

    // Gerçek (saf) calculator; @InjectMocks bunu servise enjekte eder, golden değerler korunur.
    @Spy FundamentalRatioCalculator fundamentalRatioCalculator = new FundamentalRatioCalculator();

    @InjectMocks FundamentalAnalysisService service;

    private MarketInstrument instrument;
    private CompanyFinancials financials;
    private CompanyProfile profile;
    private MarketPriceHistory latestPrice;

    @BeforeEach
    void setUp() {
        instrument = MarketInstrument.builder()
                .id(1L)
                .instrumentCode("THYAO")
                .build();

        financials = CompanyFinancials.builder()
                .id(1L)
                .instrument(instrument)
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
                .operatingCashFlow(new BigDecimal("20000000"))
                .build();

        profile = CompanyProfile.builder()
                .tickerCode("THYAO")
                .sharesOutstanding(new BigDecimal("1000000"))
                .build();

        latestPrice = MarketPriceHistory.builder()
                .closePrice(new BigDecimal("150.00"))
                .build();
    }

    @Test
    void roe_dogru_hesaplamali() {
        // ROE = net_income / total_equity * 100 = 15M / 100M * 100 = 15%
        setupMocks(List.of(financials));

        FundamentalRatios result = service.calculateRatios(1L, "2024/Y");

        assertThat(result.getRoe()).isCloseTo(new BigDecimal("15.0000"), within(new BigDecimal("0.01")));
    }

    @Test
    void graham_sayisi_hesaplamali() {
        // EPS = 15M / 1M = 15, BVPS = 100M / 1M = 100
        // Graham = sqrt(22.5 * 15 * 100) = sqrt(33750) â‰ˆ 183.71
        setupMocks(List.of(financials));

        FundamentalRatios result = service.calculateRatios(1L, "2024/Y");

        assertThat(result.getGrahamNumber()).isNotNull();
        assertThat(result.getGrahamNumber().doubleValue()).isCloseTo(183.71, within(1.0));
    }

    @Test
    void piotroski_pozitif_kriterler_yuksek_skor_vermeli() {
        // Her iki yÄ±l da pozitif verilerle karÅŸÄ±laÅŸtÄ±rma
        CompanyFinancials prevYear = CompanyFinancials.builder()
                .period("2023/Y")
                .periodType("ANNUAL")
                .revenue(new BigDecimal("90000000"))
                .grossProfit(new BigDecimal("25000000"))
                .netIncome(new BigDecimal("10000000"))
                .totalAssets(new BigDecimal("180000000"))
                .totalEquity(new BigDecimal("90000000"))
                .totalLiabilities(new BigDecimal("90000000"))
                .currentAssets(new BigDecimal("40000000"))
                .currentLiabilities(new BigDecimal("30000000"))
                .operatingCashFlow(new BigDecimal("15000000"))
                .build();
        setupMocks(List.of(financials, prevYear));

        FundamentalRatios result = service.calculateRatios(1L, "2024/Y");

        assertThat(result.getPiotroskiScore()).isNotNull();
        assertThat(result.getPiotroskiScore()).isGreaterThanOrEqualTo(6);
    }

    @Test
    void piotroski_negatif_kriterler_dusuk_skor_vermeli() {
        CompanyFinancials negativeFinancials = CompanyFinancials.builder()
                .period("2024/Y")
                .periodType("ANNUAL")
                .revenue(new BigDecimal("100000000"))
                .grossProfit(new BigDecimal("20000000"))
                .netIncome(new BigDecimal("-5000000"))  // zarar
                .totalAssets(new BigDecimal("200000000"))
                .totalEquity(new BigDecimal("80000000"))
                .totalLiabilities(new BigDecimal("120000000"))  // borÃ§ arttÄ±
                .currentAssets(new BigDecimal("20000000"))
                .currentLiabilities(new BigDecimal("40000000"))  // cari oran dÃ¼ÅŸtÃ¼
                .operatingCashFlow(new BigDecimal("-2000000"))  // negatif nakit
                .build();

        CompanyFinancials prevYear = CompanyFinancials.builder()
                .period("2023/Y")
                .periodType("ANNUAL")
                .revenue(new BigDecimal("110000000"))  // gelir dÃ¼ÅŸtÃ¼
                .grossProfit(new BigDecimal("25000000"))
                .netIncome(new BigDecimal("5000000"))
                .totalAssets(new BigDecimal("190000000"))
                .totalEquity(new BigDecimal("100000000"))
                .totalLiabilities(new BigDecimal("90000000"))
                .currentAssets(new BigDecimal("50000000"))
                .currentLiabilities(new BigDecimal("30000000"))
                .operatingCashFlow(new BigDecimal("8000000"))
                .build();

        when(marketInstrumentRepository.findById(1L)).thenReturn(Optional.of(instrument));
        when(marketPriceHistoryRepository.findTopByInstrumentAndIntervalTypeOrderByPriceTimestampDesc(instrument, IntervalType.ONE_DAY))
                .thenReturn(Optional.of(latestPrice));
        when(companyProfileRepository.findByTickerCodeIgnoreCase("THYAO")).thenReturn(Optional.of(profile));
        when(companyFinancialsRepository.findByInstrumentIdAndPeriodTypeOrderByPeriodDesc(1L, "ANNUAL"))
                .thenReturn(List.of(negativeFinancials, prevYear));
        when(fundamentalRatiosRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fundamentalHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FundamentalRatios result = service.calculateRatios(1L, "2024/Y");

        assertThat(result.getPiotroskiScore()).isNotNull();
        assertThat(result.getPiotroskiScore()).isLessThanOrEqualTo(4);
    }

    // --- Golden-value kilitleri (refactor öncesi mevcut davranış; SCALE=4 / HALF_UP, scale dahil) ---

    @Test
    void calculateRatios_locks_golden_values_for_complete_single_year_financials() {
        setupMocks(List.of(financials));

        FundamentalRatios r = service.calculateRatios(1L, "2024/Y");

        assertThat(r.getGrossMargin()).isEqualTo(new BigDecimal("30.0000"));
        assertThat(r.getNetMargin()).isEqualTo(new BigDecimal("15.0000"));
        assertThat(r.getRoe()).isEqualTo(new BigDecimal("15.0000"));
        assertThat(r.getRoa()).isEqualTo(new BigDecimal("7.5000"));
        assertThat(r.getDebtToEquity()).isEqualTo(new BigDecimal("1.0000"));
        assertThat(r.getCurrentRatio()).isEqualTo(new BigDecimal("1.6667"));
        assertThat(r.getPeRatio()).isEqualTo(new BigDecimal("10.0000"));
        assertThat(r.getPbRatio()).isEqualTo(new BigDecimal("1.5000"));
        assertThat(r.getGrahamNumber()).isEqualTo(new BigDecimal("183.7117"));
        assertThat(r.getAltmanZScore()).isEqualTo(new BigDecimal("1.7675"));
        assertThat(r.getOverallSignal()).isEqualTo("NEUTRAL");
        // Tek yıl: önceki yıl olmadığından YoY ve Piotroski hesaplanmaz.
        assertThat(r.getRevenueGrowthYoy()).isNull();
        assertThat(r.getNetIncomeGrowthYoy()).isNull();
        assertThat(r.getAssetGrowthYoy()).isNull();
        assertThat(r.getPiotroskiScore()).isNull();
    }

    @Test
    void calculateRatios_locks_yoy_growth_values_against_previous_year() {
        CompanyFinancials current = fullFinancials()
                .revenue(new BigDecimal("120000000"))
                .netIncome(new BigDecimal("18000000"))
                .totalAssets(new BigDecimal("220000000"))
                .build();
        CompanyFinancials previous = fullFinancials()
                .period("2023/Y")
                .revenue(new BigDecimal("100000000"))
                .netIncome(new BigDecimal("15000000"))
                .totalAssets(new BigDecimal("200000000"))
                .build();
        setupMocks(List.of(current, previous));

        FundamentalRatios r = service.calculateRatios(1L, "2024/Y");

        // YoY = (current - previous) * 100 / |previous|, SCALE=4
        assertThat(r.getRevenueGrowthYoy()).isEqualTo(new BigDecimal("20.0000"));   // (120-100)/100
        assertThat(r.getNetIncomeGrowthYoy()).isEqualTo(new BigDecimal("20.0000")); // (18-15)/15
        assertThat(r.getAssetGrowthYoy()).isEqualTo(new BigDecimal("10.0000"));     // (220-200)/200
        assertThat(r.getPiotroskiScore()).isNotNull();
    }

    @Test
    void calculateRatios_leaves_margins_null_when_revenue_is_missing() {
        setupMocks(List.of(fullFinancials().revenue(null).build()));

        FundamentalRatios r = service.calculateRatios(1L, "2024/Y");

        assertThat(r.getGrossMargin()).isNull();
        assertThat(r.getNetMargin()).isNull();
        // Gelirden bağımsız oranlar yine hesaplanır (response bütünüyle bozulmaz).
        assertThat(r.getRoe()).isEqualTo(new BigDecimal("15.0000"));
        assertThat(r.getRoa()).isEqualTo(new BigDecimal("7.5000"));
    }

    @Test
    void calculateRatios_skips_equity_based_ratios_when_equity_is_missing() {
        setupMocks(List.of(fullFinancials().totalEquity(null).build()));

        FundamentalRatios r = service.calculateRatios(1L, "2024/Y");

        assertThat(r.getRoe()).isNull();
        assertThat(r.getDebtToEquity()).isNull();
        assertThat(r.getPbRatio()).isNull();
        assertThat(r.getGrahamNumber()).isNull();
        assertThat(r.getAltmanZScore()).isNull();
        // Özkaynaktan bağımsız alanlar korunur.
        assertThat(r.getNetMargin()).isEqualTo(new BigDecimal("15.0000"));
        assertThat(r.getRoa()).isEqualTo(new BigDecimal("7.5000"));
        assertThat(r.getPeRatio()).isEqualTo(new BigDecimal("10.0000"));
    }

    @Test
    void calculateRatios_skips_per_share_ratios_when_shares_outstanding_missing() {
        when(marketInstrumentRepository.findById(1L)).thenReturn(Optional.of(instrument));
        when(marketPriceHistoryRepository.findTopByInstrumentAndIntervalTypeOrderByPriceTimestampDesc(instrument, IntervalType.ONE_DAY))
                .thenReturn(Optional.of(latestPrice));
        when(companyProfileRepository.findByTickerCodeIgnoreCase("THYAO")).thenReturn(Optional.empty());
        when(companyFinancialsRepository.findByInstrumentIdAndPeriodTypeOrderByPeriodDesc(1L, "ANNUAL"))
                .thenReturn(List.of(financials));
        when(fundamentalRatiosRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fundamentalHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FundamentalRatios r = service.calculateRatios(1L, "2024/Y");

        // sharesOutstanding yoksa hisse-başı tüm türev oranlar atlanır.
        assertThat(r.getPeRatio()).isNull();
        assertThat(r.getPbRatio()).isNull();
        assertThat(r.getGrahamNumber()).isNull();
        assertThat(r.getAltmanZScore()).isNull();
        // Hisse adedinden bağımsız oranlar korunur.
        assertThat(r.getRoe()).isEqualTo(new BigDecimal("15.0000"));
        assertThat(r.getCurrentRatio()).isEqualTo(new BigDecimal("1.6667"));
    }

    @Test
    void calculateRatios_uses_price_one_fallback_when_no_latest_price_exists() {
        when(marketInstrumentRepository.findById(1L)).thenReturn(Optional.of(instrument));
        when(marketPriceHistoryRepository.findTopByInstrumentAndIntervalTypeOrderByPriceTimestampDesc(instrument, IntervalType.ONE_DAY))
                .thenReturn(Optional.empty());
        when(companyProfileRepository.findByTickerCodeIgnoreCase("THYAO")).thenReturn(Optional.of(profile));
        when(companyFinancialsRepository.findByInstrumentIdAndPeriodTypeOrderByPeriodDesc(1L, "ANNUAL"))
                .thenReturn(List.of(financials));
        when(fundamentalRatiosRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fundamentalHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FundamentalRatios r = service.calculateRatios(1L, "2024/Y");

        // MEVCUT (sorunlu ama bilinçli korunan) davranış: son fiyat yoksa current price = 1 kullanılır,
        // bu da yanıltıcı şekilde düşük P/E (1/EPS) ve P/B üretir. Bu test o davranışı belgeler.
        assertThat(r.getCalculationPrice()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(r.getPeRatio()).isEqualTo(new BigDecimal("0.0667"));  // 1 / 15
        assertThat(r.getPbRatio()).isEqualTo(new BigDecimal("0.0100"));  // (1 * 1.000.000) / 100.000.000
    }

    private void setupMocks(List<CompanyFinancials> financialsList) {
        when(marketInstrumentRepository.findById(1L)).thenReturn(Optional.of(instrument));
        when(marketPriceHistoryRepository.findTopByInstrumentAndIntervalTypeOrderByPriceTimestampDesc(instrument, IntervalType.ONE_DAY))
                .thenReturn(Optional.of(latestPrice));
        when(companyProfileRepository.findByTickerCodeIgnoreCase("THYAO")).thenReturn(Optional.of(profile));
        when(companyFinancialsRepository.findByInstrumentIdAndPeriodTypeOrderByPeriodDesc(1L, "ANNUAL"))
                .thenReturn(financialsList);
        when(fundamentalRatiosRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fundamentalHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** setUp'taki {@code financials} ile aynı tam veri setini üreten builder; varyant testler tek alanı ezer. */
    private CompanyFinancials.CompanyFinancialsBuilder fullFinancials() {
        return CompanyFinancials.builder()
                .id(1L)
                .instrument(instrument)
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

