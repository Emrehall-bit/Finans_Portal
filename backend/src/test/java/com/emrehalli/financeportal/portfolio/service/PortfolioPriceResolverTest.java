package com.emrehalli.financeportal.portfolio.service;

import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import com.emrehalli.financeportal.portfolio.enums.PriceStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioPriceResolverTest {

    private final MarketQueryService marketQueryService = mock(MarketQueryService.class);
    private final PortfolioPriceResolver resolver = new PortfolioPriceResolver(marketQueryService);

    @Test
    void resolveCurrentPriceWithFallback_whenMarketQuoteExists_returnsMarketPrice() {
        Instant priceTime = Instant.parse("2026-04-27T10:15:00Z");
        MarketQuote quote = new MarketQuote(
                "BTCUSDT",
                "BTCUSDT",
                InstrumentType.CRYPTO,
                new BigDecimal("64000.50"),
                BigDecimal.ONE,
                "USDT",
                DataSource.BINANCE,
                priceTime,
                Instant.parse("2026-04-27T10:16:00Z")
        );

        when(marketQueryService.findCurrentBySymbol("BTCUSDT")).thenReturn(Optional.of(quote));

        PriceResolutionResult result = resolver.resolveCurrentPriceWithFallback(
                "BTCUSDT",
                new BigDecimal("60000"),
                LocalDateTime.of(2026, 4, 27, 9, 0)
        );

        assertTrue(result.valuationAvailable());
        assertEquals(new BigDecimal("64000.50"), result.price());
        assertEquals(PriceStatus.CACHED, result.priceStatus());
        assertEquals(LocalDateTime.of(2026, 4, 27, 10, 15), result.lastPriceUpdateTime());
    }

    @Test
    void resolveCurrentPriceWithFallback_whenMarketQuoteMissing_fallsBackToPurchasePrice() {
        when(marketQueryService.findCurrentBySymbol("THYAO")).thenReturn(Optional.empty());

        PriceResolutionResult result = resolver.resolveCurrentPriceWithFallback(
                "THYAO",
                new BigDecimal("250.25"),
                LocalDateTime.of(2026, 4, 27, 9, 0)
        );

        assertTrue(result.valuationAvailable());
        assertEquals(new BigDecimal("250.25"), result.price());
        assertEquals(PriceStatus.STALE, result.priceStatus());
        assertEquals(LocalDateTime.of(2026, 4, 27, 9, 0), result.lastPriceUpdateTime());
    }

    @Test
    void resolveCurrentPriceWithFallback_whenMarketQuoteHasNoUsablePrice_fallsBackToPurchasePrice() {
        MarketQuote quote = new MarketQuote(
                "THYAO",
                "THYAO",
                InstrumentType.STOCK,
                BigDecimal.ZERO,
                null,
                "TRY",
                DataSource.BIST,
                Instant.parse("2026-04-27T10:15:00Z"),
                Instant.parse("2026-04-27T10:16:00Z")
        );

        when(marketQueryService.findCurrentBySymbol("THYAO")).thenReturn(Optional.of(quote));

        PriceResolutionResult result = resolver.resolveCurrentPriceWithFallback(
                "THYAO",
                new BigDecimal("250.25"),
                LocalDateTime.of(2026, 4, 27, 9, 0)
        );

        assertTrue(result.valuationAvailable());
        assertEquals(new BigDecimal("250.25"), result.price());
        assertEquals(PriceStatus.STALE, result.priceStatus());
    }

    @Test
    void resolveCurrentPriceWithFallback_whenMarketLookupFails_fallsBackToPurchasePrice() {
        when(marketQueryService.findCurrentBySymbol("THYAO")).thenThrow(new IllegalStateException("cache down"));

        PriceResolutionResult result = resolver.resolveCurrentPriceWithFallback(
                "THYAO",
                new BigDecimal("250.25"),
                LocalDateTime.of(2026, 4, 27, 9, 0)
        );

        assertTrue(result.valuationAvailable());
        assertEquals(new BigDecimal("250.25"), result.price());
        assertEquals(PriceStatus.STALE, result.priceStatus());
    }

    @Test
    void resolveCurrentPriceWithFallback_whenInstrumentAndPurchasePriceAreInvalid_returnsUnavailable() {
        PriceResolutionResult result = resolver.resolveCurrentPriceWithFallback(" ", null, null);

        assertFalse(result.valuationAvailable());
        assertEquals(PriceStatus.UNAVAILABLE, result.priceStatus());
    }
}
