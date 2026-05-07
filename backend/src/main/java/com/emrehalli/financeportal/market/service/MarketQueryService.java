package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.cache.MarketCacheService;
import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.MarketPriceStatus;
import com.emrehalli.financeportal.market.exception.MarketDataNotFoundException;
import com.emrehalli.financeportal.market.persistence.entity.MarketHistoryEntity;
import com.emrehalli.financeportal.market.persistence.repository.MarketHistoryRepository;
import com.emrehalli.financeportal.market.service.model.CurrentPriceSnapshot;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MarketQueryService implements MarketPriceReader {

    private static final Logger log = LoggerFactory.getLogger(MarketQueryService.class);

    private final MarketCacheService marketCacheService;
    private final MarketHistoryRepository marketHistoryRepository;
    private final SymbolNormalizer symbolNormalizer;

    public MarketQueryService(MarketCacheService marketCacheService,
                              MarketHistoryRepository marketHistoryRepository,
                              SymbolNormalizer symbolNormalizer) {
        this.marketCacheService = marketCacheService;
        this.marketHistoryRepository = marketHistoryRepository;
        this.symbolNormalizer = symbolNormalizer;
    }

    public List<MarketQuote> getAllQuotes() {
        List<MarketQuote> cachedQuotes = marketCacheService.getAllQuotes().stream()
                .filter(this::hasUsablePrice)
                .toList();

        Map<String, MarketQuote> mergedQuotes = new LinkedHashMap<>();
        cachedQuotes.forEach(quote -> mergedQuotes.putIfAbsent(quote.symbol(), quote));

        marketHistoryRepository.findLatestBySymbol().stream()
                .filter(this::hasUsablePrice)
                .map(this::toDbFallbackQuote)
                .forEach(quote -> mergedQuotes.putIfAbsent(quote.symbol(), quote));

        return List.copyOf(mergedQuotes.values());
    }

    public Optional<MarketQuote> findCurrentBySymbol(String symbol) {
        return symbolNormalizer.normalize(symbol)
                .flatMap(this::findQuoteWithFallback);
    }

    @Override
    public CurrentPriceSnapshot resolveCurrentPrice(String symbol) {
        String canonicalSymbol = symbolNormalizer.normalize(symbol).orElse(symbol);
        if (canonicalSymbol == null || canonicalSymbol.isBlank()) {
            log.info("Market price fallback used: symbol={}, layer={}, priceStatus={}", symbol, "NONE", MarketPriceStatus.UNAVAILABLE);
            return CurrentPriceSnapshot.unavailable(symbol);
        }

        Optional<MarketQuote> liveQuote = marketCacheService.getQuoteBySymbol(canonicalSymbol)
                .filter(this::hasUsablePrice);
        if (liveQuote.isPresent()) {
            MarketQuote quote = liveQuote.get();
            Instant lastUpdatedAt = quote.fetchedAt() != null ? quote.fetchedAt() : quote.priceTime();
            log.info("Market price fallback used: symbol={}, layer={}, priceStatus={}", canonicalSymbol, "REDIS", MarketPriceStatus.LIVE);
            return new CurrentPriceSnapshot(
                    quote.symbol(),
                    quote.displayName(),
                    quote.instrumentType(),
                    quote.price(),
                    quote.changeRate(),
                    quote.currency(),
                    quote.source(),
                    quote.priceTime(),
                    quote.fetchedAt(),
                    MarketPriceStatus.LIVE,
                    lastUpdatedAt,
                    true
            );
        }

        Optional<MarketHistoryEntity> latestHistory = marketHistoryRepository.findTopBySymbolOrderByPriceDateDescIdDesc(canonicalSymbol)
                .filter(this::hasUsablePrice);
        if (latestHistory.isPresent()) {
            MarketHistoryEntity entity = latestHistory.get();
            Instant priceTime = entity.getPriceDate() == null
                    ? null
                    : entity.getPriceDate().atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant lastUpdatedAt = entity.getCreatedAt() != null ? entity.getCreatedAt() : priceTime;
            log.info("Market price fallback used: symbol={}, layer={}, priceStatus={}", canonicalSymbol, "DB", MarketPriceStatus.STALE);
            return new CurrentPriceSnapshot(
                    entity.getSymbol(),
                    entity.getDisplayName(),
                    entity.getInstrumentType(),
                    entity.getClosePrice(),
                    null,
                    entity.getCurrency(),
                    entity.getSource(),
                    priceTime,
                    lastUpdatedAt,
                    MarketPriceStatus.STALE,
                    lastUpdatedAt,
                    true
            );
        }

        log.info("Market price fallback used: symbol={}, layer={}, priceStatus={}", canonicalSymbol, "NONE", MarketPriceStatus.UNAVAILABLE);
        return CurrentPriceSnapshot.unavailable(canonicalSymbol);
    }

    public MarketQuote getQuoteBySymbol(String symbol) {
        String canonicalSymbol = symbolNormalizer.normalize(symbol)
                .orElseThrow(() -> new MarketDataNotFoundException("Market quote not found for symbol: " + symbol));

        return findQuoteWithFallback(canonicalSymbol)
                .orElseThrow(() -> new MarketDataNotFoundException("Market quote not found for symbol: " + canonicalSymbol));
    }

    private Optional<MarketQuote> findQuoteWithFallback(String canonicalSymbol) {
        Optional<MarketQuote> liveQuote = marketCacheService.getQuoteBySymbol(canonicalSymbol)
                .filter(this::hasUsablePrice);
        if (liveQuote.isPresent()) {
            return liveQuote;
        }

        return marketHistoryRepository.findTopBySymbolOrderByPriceDateDescIdDesc(canonicalSymbol)
                .filter(this::hasUsablePrice)
                .map(this::toDbFallbackQuote);
    }

    private MarketQuote toDbFallbackQuote(MarketHistoryEntity entity) {
        Instant priceTime = entity.getPriceDate() == null
                ? null
                : entity.getPriceDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant fetchedAt = entity.getCreatedAt() != null ? entity.getCreatedAt() : priceTime;
        return new MarketQuote(
                entity.getSymbol(),
                entity.getDisplayName(),
                entity.getInstrumentType(),
                entity.getClosePrice(),
                null,
                entity.getCurrency(),
                DataSource.DB_FALLBACK,
                priceTime,
                fetchedAt
        );
    }

    private boolean hasUsablePrice(MarketQuote quote) {
        return quote != null
                && quote.price() != null
                && quote.price().compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean hasUsablePrice(MarketHistoryEntity entity) {
        return entity != null
                && entity.getClosePrice() != null
                && entity.getClosePrice().compareTo(BigDecimal.ZERO) > 0;
    }
}
