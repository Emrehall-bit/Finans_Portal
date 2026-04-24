package com.emrehalli.financeportal.market.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MarketCacheService {

    private static final Logger log = LoggerFactory.getLogger(MarketCacheService.class);
    private static final TypeReference<List<MarketQuote>> MARKET_QUOTE_LIST_TYPE = new TypeReference<>() { };

    private final RedisTemplate<String, String> redisTemplate;
    private final MarketCacheTtlPolicy ttlPolicy;
    private final SymbolNormalizer symbolNormalizer;
    private final ObjectMapper objectMapper;

    public MarketCacheService(@Qualifier("marketCacheRedisTemplate") RedisTemplate<String, String> redisTemplate,
                              MarketCacheTtlPolicy ttlPolicy,
                              SymbolNormalizer symbolNormalizer,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.ttlPolicy = ttlPolicy;
        this.symbolNormalizer = symbolNormalizer;
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void putSourceQuotes(DataSource source, List<MarketQuote> quotes) {
        List<MarketQuote> safeQuotes = quotes == null ? List.of() : quotes;
        List<MarketQuote> sourceQuotes = shouldMergeSourceQuotes(source)
                ? mergeSourceQuotes(source, safeQuotes)
                : safeQuotes;

        writeValue(
                source,
                MarketCacheKeys.quotesBySource(source.name()),
                sourceQuotes,
                ttlPolicy.ttlFor(source)
        );

        putSymbolQuotes(sourceQuotes);
    }

    public List<MarketQuote> rebuildAllQuotes(Collection<DataSource> sources) {
        List<MarketQuote> mergedQuotes = sources.stream()
                .flatMap(source -> getQuotesBySource(source).stream())
                .toList();

        putAllQuotes(mergedQuotes);
        return mergedQuotes;
    }

    public void putAllQuotes(List<MarketQuote> quotes) {
        List<MarketQuote> safeQuotes = quotes == null ? List.of() : quotes;

        writeValue(null, MarketCacheKeys.ALL_QUOTES, safeQuotes, ttlPolicy.allQuotesTtl());

        putSymbolQuotes(safeQuotes);
    }

    private void putSymbolQuotes(List<MarketQuote> quotes) {
        for (MarketQuote quote : quotes) {
            symbolNormalizer.normalize(quote.symbol())
                    .ifPresent(symbol -> writeValue(
                            quote.source(),
                            MarketCacheKeys.quoteBySymbol(symbol),
                            quote,
                            quote.source() == null ? ttlPolicy.symbolQuoteTtl() : ttlPolicy.ttlFor(quote.source())
                    ));
        }
    }

    public List<MarketQuote> getQuotesBySource(DataSource source) {
        return safeReadList(MarketCacheKeys.quotesBySource(source.name()));
    }

    public List<MarketQuote> getAllQuotes() {
        return safeReadList(MarketCacheKeys.ALL_QUOTES);
    }

    public Optional<MarketQuote> getQuoteBySymbol(String symbol) {
        Optional<String> canonicalSymbol = symbolNormalizer.normalize(symbol);
        if (canonicalSymbol.isEmpty()) {
            return Optional.empty();
        }

        return safeReadQuote(MarketCacheKeys.quoteBySymbol(canonicalSymbol.get()));
    }

    private boolean shouldMergeSourceQuotes(DataSource source) {
        return source == DataSource.BIST;
    }

    private List<MarketQuote> mergeSourceQuotes(DataSource source, List<MarketQuote> quotes) {
        Map<String, MarketQuote> mergedBySymbol = new LinkedHashMap<>();

        for (MarketQuote existingQuote : getQuotesBySource(source)) {
            symbolNormalizer.normalize(existingQuote.symbol())
                    .ifPresent(symbol -> mergedBySymbol.put(symbol, existingQuote));
        }

        for (MarketQuote quote : quotes) {
            symbolNormalizer.normalize(quote.symbol())
                    .ifPresent(symbol -> mergedBySymbol.put(symbol, quote));
        }

        return List.copyOf(mergedBySymbol.values());
    }

    private void writeValue(DataSource source, String cacheKey, Object value, java.time.Duration ttl) {
        try {
            log.info("Market cache write: source={}, key={}, ttl={}", source == null ? "ALL" : source, cacheKey, ttl);
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize market cache payload for key: " + cacheKey, ex);
        }
    }

    private Optional<String> safeGet(String cacheKey) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(cacheKey));
        } catch (SerializationException ex) {
            log.warn("Market cache read failed, evicting corrupt key: key={}, error={}", cacheKey, ex.getMessage());
            redisTemplate.delete(cacheKey);
            return Optional.empty();
        }
    }

    private List<MarketQuote> safeReadList(String cacheKey) {
        Optional<String> rawValue = safeGet(cacheKey);
        if (rawValue.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            return readQuoteList(rawValue.get());
        } catch (RuntimeException ex) {
            log.warn("Market cache read failed, evicting corrupt key: key={}, error={}", cacheKey, ex.getMessage());
            redisTemplate.delete(cacheKey);
            return Collections.emptyList();
        }
    }

    private Optional<MarketQuote> safeReadQuote(String cacheKey) {
        Optional<String> rawValue = safeGet(cacheKey);
        if (rawValue.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(readQuote(rawValue.get()));
        } catch (RuntimeException ex) {
            log.warn("Market cache read failed, evicting corrupt key: key={}, error={}", cacheKey, ex.getMessage());
            redisTemplate.delete(cacheKey);
            return Optional.empty();
        }
    }

    private List<MarketQuote> readQuoteList(String rawValue) {
        JsonNode payload = readTree(rawValue);
        if (payload.isArray()) {
            return objectMapper.convertValue(payload, MARKET_QUOTE_LIST_TYPE);
        }

        return List.of(objectMapper.convertValue(payload, MarketQuote.class));
    }

    private MarketQuote readQuote(String rawValue) {
        return objectMapper.convertValue(readTree(rawValue), MarketQuote.class);
    }

    private JsonNode readTree(String rawValue) {
        try {
            return objectMapper.readTree(rawValue);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize market cache payload", ex);
        }
    }
}
