package com.emrehalli.financeportal.market.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.SerializationException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketCacheServiceTest {

    @Mock
    @Qualifier("marketCacheRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private MarketCacheTtlPolicy ttlPolicy;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final HashMap<String, String> redisStore = new HashMap<>();
    private MarketCacheService marketCacheService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(ttlPolicy.allQuotesTtl()).thenReturn(Duration.ofMinutes(5));
        lenient().when(ttlPolicy.symbolQuoteTtl()).thenReturn(Duration.ofMinutes(1));
        lenient().when(ttlPolicy.ttlFor(DataSource.EVDS)).thenReturn(Duration.ofMinutes(15));
        lenient().when(ttlPolicy.ttlFor(DataSource.BINANCE)).thenReturn(Duration.ofMinutes(1));
        lenient().when(ttlPolicy.ttlFor(DataSource.TEFAS)).thenReturn(Duration.ofDays(1));
        lenient().when(ttlPolicy.ttlFor(DataSource.BIST)).thenReturn(Duration.ofMinutes(15));
        lenient().doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), any(), any(Duration.class));
        lenient().when(valueOperations.get(anyString())).thenAnswer(invocation -> redisStore.get(invocation.getArgument(0)));

        marketCacheService = new MarketCacheService(redisTemplate, ttlPolicy, new SymbolNormalizer(), objectMapper);
    }

    @Test
    void putSourceQuotesStoresSourceAndCanonicalSymbolKeys() throws Exception {
        MarketQuote quote = quote("USDTRY", DataSource.EVDS);

        marketCacheService.putSourceQuotes(DataSource.EVDS, List.of(quote));

        List<MarketQuote> sourceQuotes = objectMapper.readValue(
                redisStore.get("market:quotes:source:EVDS"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, MarketQuote.class)
        );
        MarketQuote symbolQuote = objectMapper.readValue(redisStore.get("market:quotes:symbol:USDTRY"), MarketQuote.class);

        assertThat(sourceQuotes).singleElement().usingRecursiveComparison().ignoringFields("priceTime", "fetchedAt").isEqualTo(quote);
        assertThat(symbolQuote).usingRecursiveComparison().ignoringFields("priceTime", "fetchedAt").isEqualTo(quote);
        assertThat(marketCacheService.getQuoteBySymbol("usd/try"))
                .get()
                .usingRecursiveComparison()
                .ignoringFields("priceTime", "fetchedAt")
                .isEqualTo(quote);
        verify(valueOperations).set(eq("market:quotes:source:EVDS"), anyString(), eq(Duration.ofMinutes(15)));
        verify(valueOperations).set(eq("market:quotes:symbol:USDTRY"), anyString(), eq(Duration.ofMinutes(15)));
    }

    @Test
    void rebuildAllQuotesMergesExistingSourceCaches() throws Exception {
        MarketQuote evdsQuote = quote("USDTRY", DataSource.EVDS);
        MarketQuote binanceQuote = quote("BTCUSDT", DataSource.BINANCE);
        marketCacheService.putSourceQuotes(DataSource.EVDS, List.of(evdsQuote));
        marketCacheService.putSourceQuotes(DataSource.BINANCE, List.of(binanceQuote));

        List<MarketQuote> aggregateQuotes = marketCacheService.rebuildAllQuotes(List.of(DataSource.EVDS, DataSource.BINANCE));
        List<MarketQuote> cachedAllQuotes = objectMapper.readValue(
                redisStore.get(MarketCacheKeys.ALL_QUOTES),
                objectMapper.getTypeFactory().constructCollectionType(List.class, MarketQuote.class)
        );

        assertThat(aggregateQuotes).usingRecursiveFieldByFieldElementComparatorIgnoringFields("priceTime", "fetchedAt")
                .containsExactly(evdsQuote, binanceQuote);
        assertThat(cachedAllQuotes).usingRecursiveFieldByFieldElementComparatorIgnoringFields("priceTime", "fetchedAt")
                .containsExactly(evdsQuote, binanceQuote);
        verify(valueOperations).set(eq(MarketCacheKeys.ALL_QUOTES), anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void readsJsonPayloadsFromRedisBackToMarketQuote() throws Exception {
        MarketQuote quote = quote("USDTRY", DataSource.EVDS);
        redisStore.put(MarketCacheKeys.quotesBySource(DataSource.EVDS.name()), objectMapper.writeValueAsString(List.of(quote)));
        redisStore.put(MarketCacheKeys.quoteBySymbol("USDTRY"), objectMapper.writeValueAsString(quote));

        assertThat(marketCacheService.getQuotesBySource(DataSource.EVDS))
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("priceTime", "fetchedAt")
                .containsExactly(quote);
        assertThat(marketCacheService.getQuoteBySymbol("usd/try"))
                .get()
                .usingRecursiveComparison()
                .ignoringFields("priceTime", "fetchedAt")
                .isEqualTo(quote);
    }

    @Test
    void mergesBistSourceCacheAcrossPartialRefreshes() throws Exception {
        MarketQuote thyao = quote("THYAO", DataSource.BIST);
        MarketQuote asels = quote("ASELS", DataSource.BIST);

        marketCacheService.putSourceQuotes(DataSource.BIST, List.of(thyao));
        marketCacheService.putSourceQuotes(DataSource.BIST, List.of(asels));

        List<MarketQuote> sourceQuotes = objectMapper.readValue(
                redisStore.get("market:quotes:source:BIST"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, MarketQuote.class)
        );

        assertThat(sourceQuotes).extracting(MarketQuote::symbol).containsExactly("THYAO", "ASELS");
        assertThat(marketCacheService.getQuoteBySymbol("THYAO")).isPresent();
        assertThat(marketCacheService.getQuoteBySymbol("ASELS")).isPresent();
        verify(valueOperations, times(2)).set(eq("market:quotes:source:BIST"), anyString(), eq(Duration.ofMinutes(15)));
    }

    @Test
    void evictsKeyWhenRedisJsonPayloadIsCorrupt() {
        redisStore.put(MarketCacheKeys.quotesBySource(DataSource.EVDS.name()), "not-json");

        assertThat(marketCacheService.getQuotesBySource(DataSource.EVDS)).isEmpty();
        verify(redisTemplate).delete(MarketCacheKeys.quotesBySource(DataSource.EVDS.name()));
    }

    @Test
    void returnsEmptyListAndEvictsKeyWhenRedisSerializerFails() {
        when(valueOperations.get(MarketCacheKeys.quotesBySource(DataSource.EVDS.name())))
                .thenThrow(new SerializationException("bad json"));

        assertThat(marketCacheService.getQuotesBySource(DataSource.EVDS)).isEmpty();
        verify(redisTemplate).delete(MarketCacheKeys.quotesBySource(DataSource.EVDS.name()));
    }

    private static MarketQuote quote(String symbol, DataSource source) {
        Instant now = Instant.now();
        return new MarketQuote(
                symbol,
                symbol,
                source == DataSource.BINANCE ? InstrumentType.CRYPTO : source == DataSource.BIST ? InstrumentType.STOCK : InstrumentType.FX,
                BigDecimal.ONE,
                null,
                "TRY",
                source,
                now,
                now
        );
    }
}
