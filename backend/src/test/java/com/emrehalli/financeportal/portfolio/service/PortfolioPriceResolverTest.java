package com.emrehalli.financeportal.portfolio.service;

import com.emrehalli.financeportal.market.service.MarketQueryService;
import com.emrehalli.financeportal.portfolio.enums.PriceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioPriceResolverTest {

    private MarketQueryService marketQueryService;
    private PortfolioPriceResolver resolver;

    @BeforeEach
    void setUp() {
        marketQueryService = mock(MarketQueryService.class);
        resolver = new PortfolioPriceResolver(marketQueryService);
    }

    @Test
    void resolve_returns_one_for_try_currency() {
        PriceResolutionResult result = resolver.resolveCurrentPriceWithFallback("TRY", null, null);

        assertThat(result.valuationAvailable()).isTrue();
        assertThat(result.price()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.priceStatus()).isEqualTo(PriceStatus.LIVE);
    }

    @Test
    void resolve_returns_live_price_when_market_price_available() {
        BigDecimal marketPrice = new BigDecimal("152.40");
        BigDecimal changeRate = new BigDecimal("1.25");
        LocalDateTime fetchedAt = LocalDateTime.of(2026, 6, 1, 10, 0);

        when(marketQueryService.findBySymbol("THYAO")).thenReturn(Optional.of(
                new MarketQueryService.MarketSnapshot(
                        "THYAO", "Turk Hava Yollari", marketPrice, changeRate,
                        "BIST", "STOCK", "TRY", fetchedAt,
                        null, null, null, null, null)));

        PriceResolutionResult result = resolver.resolveCurrentPriceWithFallback(
                "THYAO", new BigDecimal("100.00"), LocalDateTime.of(2026, 5, 1, 0, 0));

        assertThat(result.valuationAvailable()).isTrue();
        assertThat(result.price()).isEqualByComparingTo("152.40");
        assertThat(result.priceStatus()).isEqualTo(PriceStatus.LIVE);
        assertThat(result.lastPriceUpdateTime()).isEqualTo(fetchedAt);
    }

    @Test
    void resolve_falls_back_to_purchase_price_when_market_price_unavailable() {
        BigDecimal purchasePrice = new BigDecimal("100.00");
        LocalDateTime purchaseDate = LocalDateTime.of(2026, 5, 1, 0, 0);

        when(marketQueryService.findBySymbol("THYAO")).thenReturn(Optional.empty());

        PriceResolutionResult result = resolver.resolveCurrentPriceWithFallback("THYAO", purchasePrice, purchaseDate);

        assertThat(result.valuationAvailable()).isTrue();
        assertThat(result.price()).isEqualByComparingTo("100.00");
        assertThat(result.priceStatus()).isEqualTo(PriceStatus.STALE);
        assertThat(result.lastPriceUpdateTime()).isEqualTo(purchaseDate);
    }

    @Test
    void resolve_returns_unavailable_when_both_market_and_purchase_price_missing() {
        when(marketQueryService.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

        PriceResolutionResult result = resolver.resolveCurrentPriceWithFallback("UNKNOWN", null, null);

        assertThat(result.valuationAvailable()).isFalse();
        assertThat(result.price()).isNull();
        assertThat(result.priceStatus()).isEqualTo(PriceStatus.UNAVAILABLE);
    }

    @Test
    void resolve_returns_unavailable_when_purchase_price_is_zero() {
        when(marketQueryService.findBySymbol("THYAO")).thenReturn(Optional.empty());

        PriceResolutionResult result = resolver.resolveCurrentPriceWithFallback(
                "THYAO", BigDecimal.ZERO, LocalDateTime.now());

        assertThat(result.valuationAvailable()).isFalse();
        assertThat(result.priceStatus()).isEqualTo(PriceStatus.UNAVAILABLE);
    }
}
