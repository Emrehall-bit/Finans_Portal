package com.emrehalli.financeportal.portfolio.service;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.portfolio.dto.PortfolioHoldingDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioPerformancePointDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioPerformanceResponse;
import com.emrehalli.financeportal.portfolio.dto.PortfolioRiskSummaryResponse;
import com.emrehalli.financeportal.portfolio.enums.PriceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioRiskAnalysisServiceTest {

    private PortfolioHoldingService portfolioHoldingService;
    private PortfolioPerformanceHistoryService portfolioPerformanceHistoryService;
    private MarketInstrumentRepository marketInstrumentRepository;
    private MarketPriceHistoryRepository marketPriceHistoryRepository;
    private PortfolioRiskAnalysisService service;

    @BeforeEach
    void setUp() {
        portfolioHoldingService = mock(PortfolioHoldingService.class);
        portfolioPerformanceHistoryService = mock(PortfolioPerformanceHistoryService.class);
        marketInstrumentRepository = mock(MarketInstrumentRepository.class);
        marketPriceHistoryRepository = mock(MarketPriceHistoryRepository.class);
        service = new PortfolioRiskAnalysisService(
                portfolioHoldingService,
                portfolioPerformanceHistoryService,
                marketInstrumentRepository,
                marketPriceHistoryRepository
        );
    }

    @Test
    void hhi_diversification_score_single_position_is_lowest_and_triggers_concentration_alert() {
        Long portfolioId = 1L;
        MarketInstrument stock = stockInstrument("THYAO");

        PortfolioHoldingDto holding = holding("THYAO", new BigDecimal("1000.00"), new BigDecimal("10"), new BigDecimal("100.00"));
        when(portfolioHoldingService.getPortfolioValuation(portfolioId))
                .thenReturn(new PortfolioValuationResult(List.of(holding), null));
        when(marketInstrumentRepository.findByInstrumentCodeIgnoreCase("THYAO"))
                .thenReturn(Optional.of(stock));
        when(portfolioPerformanceHistoryService.getPerformanceHistory(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new PortfolioPerformanceResponse(List.of(), null, null, true));

        PortfolioRiskSummaryResponse result = service.getRiskSummary(portfolioId);

        // Single position = 100% weight >= 55% threshold
        assertThat(result.alertTitle()).isEqualTo("Yüksek konsantrasyon riski");
        assertThat(result.alertTone()).isEqualTo("warning");
        // Diversification score is low for a single position
        assertThat(result.diversification().score()).isLessThan(30);
    }

    @Test
    void hhi_diversification_score_equal_weights_returns_better_diversification() {
        Long portfolioId = 2L;
        MarketInstrument stock = stockInstrument("THYAO");
        MarketInstrument stock2 = stockInstrument("GARAN");
        MarketInstrument stock3 = stockInstrument("AKBNK");

        List<PortfolioHoldingDto> holdings = List.of(
                holding("THYAO", new BigDecimal("1000.00"), new BigDecimal("10"), new BigDecimal("100.00")),
                holding("GARAN", new BigDecimal("1000.00"), new BigDecimal("20"), new BigDecimal("50.00")),
                holding("AKBNK", new BigDecimal("1000.00"), new BigDecimal("5"), new BigDecimal("200.00"))
        );

        when(portfolioHoldingService.getPortfolioValuation(portfolioId))
                .thenReturn(new PortfolioValuationResult(holdings, null));
        when(marketInstrumentRepository.findByInstrumentCodeIgnoreCase("THYAO")).thenReturn(Optional.of(stock));
        when(marketInstrumentRepository.findByInstrumentCodeIgnoreCase("GARAN")).thenReturn(Optional.of(stock2));
        when(marketInstrumentRepository.findByInstrumentCodeIgnoreCase("AKBNK")).thenReturn(Optional.of(stock3));
        when(portfolioPerformanceHistoryService.getPerformanceHistory(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new PortfolioPerformanceResponse(List.of(), null, null, true));

        PortfolioRiskSummaryResponse result = service.getRiskSummary(portfolioId);

        // 3 equal positions, max weight = 33.33% < 55%
        assertThat(result.alertTitle()).isNotEqualTo("Yüksek konsantrasyon riski");
        // Diversification score for 3 equal positions should be much higher than for 1
        assertThat(result.diversification().score()).isGreaterThan(50);
    }

    @Test
    void risk_alert_high_concentration_when_top_position_above_55_percent() {
        Long portfolioId = 3L;
        MarketInstrument stock = stockInstrument("THYAO");

        // THYAO = 800, GARAN = 200 → THYAO weight = 80% > 55%
        List<PortfolioHoldingDto> holdings = List.of(
                holding("THYAO", new BigDecimal("800.00"), new BigDecimal("8"), new BigDecimal("100.00")),
                holding("GARAN", new BigDecimal("200.00"), new BigDecimal("4"), new BigDecimal("50.00"))
        );

        when(portfolioHoldingService.getPortfolioValuation(portfolioId))
                .thenReturn(new PortfolioValuationResult(holdings, null));
        when(marketInstrumentRepository.findByInstrumentCodeIgnoreCase(any()))
                .thenReturn(Optional.of(stock));
        when(portfolioPerformanceHistoryService.getPerformanceHistory(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new PortfolioPerformanceResponse(List.of(), null, null, true));

        PortfolioRiskSummaryResponse result = service.getRiskSummary(portfolioId);

        assertThat(result.alertTitle()).isEqualTo("Yüksek konsantrasyon riski");
        assertThat(result.alertMessage()).contains("THYAO");
        assertThat(result.alertTone()).isEqualTo("warning");
    }

    @Test
    void risk_alert_is_neutral_when_positions_are_moderately_distributed() {
        Long portfolioId = 4L;
        MarketInstrument stock = stockInstrument("THYAO");
        MarketInstrument stock2 = stockInstrument("GARAN");

        // Equal 50%-50% split → max weight 50% < 55% threshold
        List<PortfolioHoldingDto> holdings = List.of(
                holding("THYAO", new BigDecimal("500.00"), new BigDecimal("5"), new BigDecimal("100.00")),
                holding("GARAN", new BigDecimal("500.00"), new BigDecimal("10"), new BigDecimal("50.00"))
        );

        when(portfolioHoldingService.getPortfolioValuation(portfolioId))
                .thenReturn(new PortfolioValuationResult(holdings, null));
        when(marketInstrumentRepository.findByInstrumentCodeIgnoreCase("THYAO")).thenReturn(Optional.of(stock));
        when(marketInstrumentRepository.findByInstrumentCodeIgnoreCase("GARAN")).thenReturn(Optional.of(stock2));
        when(portfolioPerformanceHistoryService.getPerformanceHistory(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new PortfolioPerformanceResponse(List.of(), null, null, true));

        PortfolioRiskSummaryResponse result = service.getRiskSummary(portfolioId);

        // No concentration risk. STOCK fallback liquidity = 46 < 60, diversif >= 70 not both met → neutral
        assertThat(result.alertTitle()).isEqualTo("Risk görünümü dengeli");
        assertThat(result.alertTone()).isEqualTo("neutral");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private MarketInstrument stockInstrument(String code) {
        return MarketInstrument.builder()
                .id(1L)
                .instrumentCode(code)
                .instrumentType(InstrumentType.STOCK)
                .sourceName(SourceName.BIST)
                .build();
    }

    private PortfolioHoldingDto holding(String code, BigDecimal currentValue, BigDecimal quantity, BigDecimal buyPrice) {
        return PortfolioHoldingDto.builder()
                .instrumentCode(code)
                .quantity(quantity)
                .buyPrice(buyPrice)
                .currentPrice(currentValue.divide(quantity, 4, java.math.RoundingMode.HALF_UP))
                .currentValue(currentValue)
                .priceStatus(PriceStatus.LIVE)
                .valuationAvailable(true)
                .build();
    }

    private PortfolioPerformancePointDto point(LocalDate date, double totalValue) {
        BigDecimal val = BigDecimal.valueOf(totalValue);
        return new PortfolioPerformancePointDto(date, val, val, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
