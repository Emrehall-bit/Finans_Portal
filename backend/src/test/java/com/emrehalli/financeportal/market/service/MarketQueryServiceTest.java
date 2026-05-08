package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.cache.MarketCacheService;
import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.MarketPriceStatus;
import com.emrehalli.financeportal.market.persistence.entity.MarketHistoryEntity;
import com.emrehalli.financeportal.market.persistence.repository.MarketHistoryRepository;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MarketQueryServiceTest {

    @Mock
    private MarketCacheService marketCacheService;

    @Mock
    private MarketHistoryRepository marketHistoryRepository;

    @Test
    void fallsBackToLatestDbHistoryWhenRedisQuoteIsMissing() {
        MarketQueryService service = new MarketQueryService(marketCacheService, marketHistoryRepository, new SymbolNormalizer());
        when(marketCacheService.getQuoteBySymbol("THYAO")).thenReturn(Optional.empty());
        when(marketHistoryRepository.findTopBySymbolOrderByPriceDateDescIdDesc("THYAO")).thenReturn(Optional.of(historyEntity()));

        var result = service.resolveCurrentPrice("THYAO");

        assertThat(result.price()).isEqualByComparingTo("320.400000");
        assertThat(result.source()).isEqualTo(DataSource.BIST);
        assertThat(result.priceStatus()).isEqualTo(MarketPriceStatus.STALE);
        assertThat(result.priceAvailable()).isTrue();
    }

    @Test
    void returnsUnavailableWhenRedisAndDbAreMissing() {
        MarketQueryService service = new MarketQueryService(marketCacheService, marketHistoryRepository, new SymbolNormalizer());
        when(marketCacheService.getQuoteBySymbol("THYAO")).thenReturn(Optional.empty());
        when(marketHistoryRepository.findTopBySymbolOrderByPriceDateDescIdDesc("THYAO")).thenReturn(Optional.empty());

        var result = service.resolveCurrentPrice("THYAO");

        assertThat(result.symbol()).isEqualTo("THYAO");
        assertThat(result.priceStatus()).isEqualTo(MarketPriceStatus.UNAVAILABLE);
        assertThat(result.priceAvailable()).isFalse();
        assertThat(result.price()).isNull();
    }

    @Test
    void prefersRedisQuoteWhenAvailable() {
        MarketQueryService service = new MarketQueryService(marketCacheService, marketHistoryRepository, new SymbolNormalizer());
        when(marketCacheService.getQuoteBySymbol("BTCUSDT")).thenReturn(Optional.of(liveQuote()));

        var result = service.resolveCurrentPrice("BTCUSDT");

        assertThat(result.price()).isEqualByComparingTo("93500.100000");
        assertThat(result.priceStatus()).isEqualTo(MarketPriceStatus.LIVE);
    }

    @Test
    void getAllQuotesFallsBackToLatestDbHistoryWhenAggregateCacheIsEmpty() {
        MarketQueryService service = new MarketQueryService(marketCacheService, marketHistoryRepository, new SymbolNormalizer());
        when(marketCacheService.getAllQuotes()).thenReturn(java.util.List.of());
        when(marketHistoryRepository.findLatestBySymbol()).thenReturn(java.util.List.of(historyEntity()));

        var result = service.getAllQuotes();

        assertThat(result).singleElement().satisfies(quote -> {
            assertThat(quote.symbol()).isEqualTo("THYAO");
            assertThat(quote.source()).isEqualTo(DataSource.BIST);
            assertThat(quote.priceStatus()).isEqualTo(MarketPriceStatus.STALE);
            assertThat(quote.changeRate()).isNull();
        });
    }

    @Test
    void getAllQuotesDoesNotHitDbWhenAggregateCacheHasLiveQuotes() {
        MarketQueryService service = new MarketQueryService(marketCacheService, marketHistoryRepository, new SymbolNormalizer());
        when(marketCacheService.getAllQuotes()).thenReturn(java.util.List.of(liveQuote()));
        when(marketHistoryRepository.findLatestBySymbol()).thenReturn(java.util.List.of());

        var result = service.getAllQuotes();

        assertThat(result).singleElement().satisfies(quote -> assertThat(quote.source()).isEqualTo(DataSource.BINANCE));
        verify(marketHistoryRepository).findLatestBySymbol();
    }

    @Test
    void getAllQuotesMergesDbFallbackForSymbolsMissingFromAggregateCache() {
        MarketQueryService service = new MarketQueryService(marketCacheService, marketHistoryRepository, new SymbolNormalizer());
        when(marketCacheService.getAllQuotes()).thenReturn(java.util.List.of(liveQuote()));
        when(marketHistoryRepository.findLatestBySymbol()).thenReturn(java.util.List.of(historyEntity()));

        var result = service.getAllQuotes();

        assertThat(result).hasSize(2);
        assertThat(result).anySatisfy(quote -> {
            assertThat(quote.symbol()).isEqualTo("BTCUSDT");
            assertThat(quote.source()).isEqualTo(DataSource.BINANCE);
        });
        assertThat(result).anySatisfy(quote -> {
            assertThat(quote.symbol()).isEqualTo("THYAO");
            assertThat(quote.source()).isEqualTo(DataSource.BIST);
            assertThat(quote.priceStatus()).isEqualTo(MarketPriceStatus.STALE);
            assertThat(quote.changeRate()).isNull();
        });
    }

    @Test
    void getQuoteBySymbolFallsBackToLatestDbHistoryWhenRedisQuoteIsMissing() {
        MarketQueryService service = new MarketQueryService(marketCacheService, marketHistoryRepository, new SymbolNormalizer());
        when(marketCacheService.getQuoteBySymbol("THYAO")).thenReturn(Optional.empty());
        when(marketHistoryRepository.findTopBySymbolOrderByPriceDateDescIdDesc("THYAO")).thenReturn(Optional.of(historyEntity()));

        MarketQuote result = service.getQuoteBySymbol("THYAO");

        assertThat(result.symbol()).isEqualTo("THYAO");
        assertThat(result.price()).isEqualByComparingTo("320.400000");
        assertThat(result.changeRate()).isNull();
        assertThat(result.source()).isEqualTo(DataSource.BIST);
        assertThat(result.priceStatus()).isEqualTo(MarketPriceStatus.STALE);
    }

    @Test
    void findCurrentBySymbolFallsBackToLatestDbHistoryWhenRedisQuoteIsMissing() {
        MarketQueryService service = new MarketQueryService(marketCacheService, marketHistoryRepository, new SymbolNormalizer());
        when(marketCacheService.getQuoteBySymbol("THYAO")).thenReturn(Optional.empty());
        when(marketHistoryRepository.findTopBySymbolOrderByPriceDateDescIdDesc("THYAO")).thenReturn(Optional.of(historyEntity()));

        Optional<MarketQuote> result = service.findCurrentBySymbol("THYAO");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().source()).isEqualTo(DataSource.BIST);
        assertThat(result.orElseThrow().priceStatus()).isEqualTo(MarketPriceStatus.STALE);
        assertThat(result.orElseThrow().changeRate()).isNull();
    }

    @Test
    void getDefaultQuotesExcludesMacroIndicatorEntries() {
        MarketQueryService service = new MarketQueryService(marketCacheService, marketHistoryRepository, new SymbolNormalizer());
        when(marketCacheService.getAllQuotes()).thenReturn(java.util.List.of(liveQuote(), macroQuote()));
        when(marketHistoryRepository.findLatestBySymbol()).thenReturn(java.util.List.of());

        var result = service.getDefaultQuotes();

        assertThat(result).extracting(MarketQuote::symbol)
                .containsExactly("BTCUSDT");
    }

    private MarketQuote liveQuote() {
        Instant now = Instant.parse("2026-05-04T00:00:00Z");
        return new MarketQuote(
                "BTCUSDT",
                "Bitcoin / Tether",
                InstrumentType.CRYPTO,
                new BigDecimal("93500.100000"),
                new BigDecimal("4.2000"),
                "USDT",
                DataSource.BINANCE,
                now,
                now,
                MarketPriceStatus.LIVE
        );
    }

    private MarketQuote macroQuote() {
        Instant now = Instant.parse("2026-05-04T00:00:00Z");
        return new MarketQuote(
                "TCMBFAIZ",
                "TCMB Politika Faizi (%)",
                InstrumentType.MACRO_INDICATOR,
                new BigDecimal("46.000000"),
                new BigDecimal("0.5000"),
                "TRY",
                DataSource.EVDS,
                now,
                now,
                MarketPriceStatus.LIVE
        );
    }

    private MarketHistoryEntity historyEntity() {
        MarketHistoryEntity entity = new MarketHistoryEntity();
        entity.setSymbol("THYAO");
        entity.setDisplayName("Turk Hava Yollari");
        entity.setInstrumentType(InstrumentType.STOCK);
        entity.setSource(DataSource.BIST);
        entity.setPriceDate(LocalDate.of(2026, 5, 3));
        entity.setClosePrice(new BigDecimal("320.400000"));
        entity.setCurrency("TRY");
        entity.setCreatedAt(Instant.parse("2026-05-04T00:00:00Z"));
        return entity;
    }
}
