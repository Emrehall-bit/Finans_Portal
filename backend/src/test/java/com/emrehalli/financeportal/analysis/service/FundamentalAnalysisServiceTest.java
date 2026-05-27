package com.emrehalli.financeportal.analysis.service;

import com.emrehalli.financeportal.analysis.entity.CompanyFinancials;
import com.emrehalli.financeportal.analysis.entity.FundamentalHistory;
import com.emrehalli.financeportal.analysis.entity.FundamentalRatios;
import com.emrehalli.financeportal.analysis.repository.CompanyFinancialsRepository;
import com.emrehalli.financeportal.analysis.repository.FundamentalHistoryRepository;
import com.emrehalli.financeportal.analysis.repository.FundamentalRatiosRepository;
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
        // Graham = sqrt(22.5 * 15 * 100) = sqrt(33750) ≈ 183.71
        setupMocks(List.of(financials));

        FundamentalRatios result = service.calculateRatios(1L, "2024/Y");

        assertThat(result.getGrahamNumber()).isNotNull();
        assertThat(result.getGrahamNumber().doubleValue()).isCloseTo(183.71, within(1.0));
    }

    @Test
    void piotroski_pozitif_kriterler_yuksek_skor_vermeli() {
        // Her iki yıl da pozitif verilerle karşılaştırma
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
                .totalLiabilities(new BigDecimal("120000000"))  // borç arttı
                .currentAssets(new BigDecimal("20000000"))
                .currentLiabilities(new BigDecimal("40000000"))  // cari oran düştü
                .operatingCashFlow(new BigDecimal("-2000000"))  // negatif nakit
                .build();

        CompanyFinancials prevYear = CompanyFinancials.builder()
                .period("2023/Y")
                .periodType("ANNUAL")
                .revenue(new BigDecimal("110000000"))  // gelir düştü
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
        when(companyFinancialsRepository.findTopByInstrumentIdAndPeriodTypeOrderByPeriodDesc(1L, "ANNUAL"))
                .thenReturn(Optional.of(negativeFinancials));
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

    private void setupMocks(List<CompanyFinancials> financialsList) {
        when(marketInstrumentRepository.findById(1L)).thenReturn(Optional.of(instrument));
        when(companyFinancialsRepository.findTopByInstrumentIdAndPeriodTypeOrderByPeriodDesc(1L, "ANNUAL"))
                .thenReturn(Optional.of(financialsList.get(0)));
        when(marketPriceHistoryRepository.findTopByInstrumentAndIntervalTypeOrderByPriceTimestampDesc(instrument, IntervalType.ONE_DAY))
                .thenReturn(Optional.of(latestPrice));
        when(companyProfileRepository.findByTickerCodeIgnoreCase("THYAO")).thenReturn(Optional.of(profile));
        when(companyFinancialsRepository.findByInstrumentIdAndPeriodTypeOrderByPeriodDesc(1L, "ANNUAL"))
                .thenReturn(financialsList);
        when(fundamentalRatiosRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fundamentalHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }
}
