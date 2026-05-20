package com.emrehalli.financeportal.portfolio.service;

import com.emrehalli.financeportal.market.service.MarketQueryService;
import com.emrehalli.financeportal.portfolio.dto.PortfolioPerformanceResponse;
import com.emrehalli.financeportal.portfolio.entity.Portfolio;
import com.emrehalli.financeportal.portfolio.entity.PortfolioHolding;
import com.emrehalli.financeportal.portfolio.entity.PortfolioVisibility;
import com.emrehalli.financeportal.portfolio.repository.PortfolioHoldingRepository;
import com.emrehalli.financeportal.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioPerformanceHistoryServiceTest {

    private PortfolioService portfolioService;
    private PortfolioHoldingRepository portfolioHoldingRepository;
    private MarketQueryService marketQueryService;
    private PortfolioPerformanceHistoryService service;

    @BeforeEach
    void setUp() {
        portfolioService = mock(PortfolioService.class);
        portfolioHoldingRepository = mock(PortfolioHoldingRepository.class);
        marketQueryService = mock(MarketQueryService.class);
        service = new PortfolioPerformanceHistoryService(portfolioService, portfolioHoldingRepository, marketQueryService);
    }

    @Test
    void buildsDailyPortfolioSeriesFromHoldingsAndHistory() {
        Long portfolioId = 7L;
        when(portfolioService.getPortfolioEntityById(portfolioId)).thenReturn(portfolio(portfolioId));
        when(portfolioHoldingRepository.findByPortfolioId(portfolioId)).thenReturn(List.of(
                stockHolding(),
                cashHolding()
        ));
        when(marketQueryService.getHistory("THYAO", null, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2)))
                .thenReturn(List.of(
                        new MarketQueryService.HistoricalPrice("THYAO", LocalDate.of(2026, 5, 1), BigDecimal.valueOf(11)),
                        new MarketQueryService.HistoricalPrice("THYAO", LocalDate.of(2026, 5, 2), BigDecimal.valueOf(12))
                ));

        PortfolioPerformanceResponse response = service.getPerformanceHistory(
                portfolioId,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 2)
        );

        assertThat(response.approximate()).isTrue();
        assertThat(response.points()).hasSize(2);

        assertThat(response.points().get(0).date()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(response.points().get(0).totalCost()).isEqualByComparingTo("20");
        assertThat(response.points().get(0).totalValue()).isEqualByComparingTo("22");
        assertThat(response.points().get(0).profitLoss()).isEqualByComparingTo("2");
        assertThat(response.points().get(0).profitLossPercent()).isEqualByComparingTo("10.0000");

        assertThat(response.points().get(1).date()).isEqualTo(LocalDate.of(2026, 5, 2));
        assertThat(response.points().get(1).totalCost()).isEqualByComparingTo("120");
        assertThat(response.points().get(1).totalValue()).isEqualByComparingTo("124");
        assertThat(response.points().get(1).profitLoss()).isEqualByComparingTo("4");
        assertThat(response.points().get(1).profitLossPercent()).isEqualByComparingTo("3.3333");
    }

    @Test
    void fallsBackToBuyPriceWhenHistoryMissing() {
        Long portfolioId = 9L;
        when(portfolioService.getPortfolioEntityById(portfolioId)).thenReturn(portfolio(portfolioId));
        when(portfolioHoldingRepository.findByPortfolioId(portfolioId)).thenReturn(List.of(stockHolding()));
        when(marketQueryService.getHistory("THYAO", null, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1)))
                .thenReturn(List.of());

        PortfolioPerformanceResponse response = service.getPerformanceHistory(
                portfolioId,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 1)
        );

        assertThat(response.points()).hasSize(1);
        assertThat(response.points().getFirst().totalValue()).isEqualByComparingTo("20");
        assertThat(response.points().getFirst().profitLoss()).isEqualByComparingTo("0");
    }

    private Portfolio portfolio(Long id) {
        return Portfolio.builder()
                .id(id)
                .portfolioName("Test Portfoy")
                .visibilityStatus(PortfolioVisibility.PRIVATE)
                .createdAt(LocalDateTime.of(2026, 5, 1, 10, 0))
                .user(User.builder().id(1L).keycloakId("user-1").build())
                .build();
    }

    private PortfolioHolding stockHolding() {
        return PortfolioHolding.builder()
                .id(1L)
                .portfolio(portfolio(7L))
                .instrumentCode("THYAO")
                .quantity(BigDecimal.valueOf(2))
                .buyPrice(BigDecimal.TEN)
                .purchaseDate(LocalDate.of(2026, 5, 1))
                .createdAt(LocalDateTime.of(2026, 5, 1, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 5, 1, 10, 0))
                .build();
    }

    private PortfolioHolding cashHolding() {
        return PortfolioHolding.builder()
                .id(2L)
                .portfolio(portfolio(7L))
                .instrumentCode("TRY")
                .quantity(BigDecimal.valueOf(100))
                .buyPrice(BigDecimal.ONE)
                .purchaseDate(LocalDate.of(2026, 5, 2))
                .createdAt(LocalDateTime.of(2026, 5, 2, 9, 0))
                .updatedAt(LocalDateTime.of(2026, 5, 2, 9, 0))
                .build();
    }
}
