package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.market.support.BinancePairMapper;
import com.emrehalli.financeportal.technicalanalysis.dto.TechnicalCandleDto;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TechnicalCandleServiceTest {

    /**
     * Sabit saat: testlerdeki Mayıs 2026 mumlarının seçilen aralık (ör. "1m") içinde kalmasını
     * ve aralık-dışı kırpmanın deterministik olmasını sağlar.
     */
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void getCandles_should_not_return_stock_candles_from_is_yatirim_history() {
        MarketInstrumentRepository instrumentRepository = mock(MarketInstrumentRepository.class);
        MarketPriceHistoryRepository historyRepository = mock(MarketPriceHistoryRepository.class);
        BinancePairMapper binancePairMapper = mock(BinancePairMapper.class);

        MarketInstrument instrument = MarketInstrument.builder()
                .instrumentCode("THYAO")
                .instrumentType(InstrumentType.STOCK)
                .sourceName(SourceName.IS_YATIRIM)
                .build();

        when(binancePairMapper.toDisplayCode("THYAO")).thenReturn("THYAO");
        when(instrumentRepository.findAllByInstrumentCodeInAndSourceName(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(SourceName.BINANCE)))
                .thenReturn(List.of());
        when(instrumentRepository.findByInstrumentCodeIgnoreCase("THYAO"))
                .thenReturn(Optional.of(instrument));
        when(historyRepository.findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                org.mockito.ArgumentMatchers.eq(instrument),
                org.mockito.ArgumentMatchers.eq(IntervalType.ONE_DAY),
                org.mockito.ArgumentMatchers.eq(SourceName.YAHOO_FINANCE),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of());

        TechnicalCandleService service = new TechnicalCandleService(
                instrumentRepository,
                historyRepository,
                new IndicatorSeriesCalculator(new MovingAverageService(), new RsiService()),
                binancePairMapper,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.getCandles("THYAO", "6m", "1d"))
                .isInstanceOf(TechnicalAnalysisException.NotFound.class)
                .hasMessageContaining("Candlestick data not available for symbol THYAO");
    }

    @Test
    void getCandles_should_return_stock_candles_from_yahoo_complete_ohlcv_history() {
        MarketInstrumentRepository instrumentRepository = mock(MarketInstrumentRepository.class);
        MarketPriceHistoryRepository historyRepository = mock(MarketPriceHistoryRepository.class);
        BinancePairMapper binancePairMapper = mock(BinancePairMapper.class);

        MarketInstrument instrument = MarketInstrument.builder()
                .instrumentCode("THYAO")
                .instrumentType(InstrumentType.STOCK)
                .sourceName(SourceName.BIST)
                .build();

        when(binancePairMapper.toDisplayCode("THYAO")).thenReturn("THYAO");
        when(instrumentRepository.findAllByInstrumentCodeInAndSourceName(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(SourceName.BINANCE)))
                .thenReturn(List.of());
        when(instrumentRepository.findByInstrumentCodeIgnoreCase("THYAO"))
                .thenReturn(Optional.of(instrument));

        MarketPriceHistory yahooBar = MarketPriceHistory.builder()
                .instrument(instrument)
                .intervalType(IntervalType.ONE_DAY)
                .sourceName(SourceName.YAHOO_FINANCE)
                .priceTimestamp(Instant.parse("2026-05-01T00:00:00Z"))
                .openPrice(BigDecimal.valueOf(318))
                .highPrice(BigDecimal.valueOf(325))
                .lowPrice(BigDecimal.valueOf(315))
                .closePrice(BigDecimal.valueOf(321))
                .volume(BigDecimal.valueOf(1000))
                .build();

        when(historyRepository.findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                org.mockito.ArgumentMatchers.eq(instrument),
                org.mockito.ArgumentMatchers.eq(IntervalType.ONE_DAY),
                org.mockito.ArgumentMatchers.eq(SourceName.YAHOO_FINANCE),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of(yahooBar));

        TechnicalCandleService service = new TechnicalCandleService(
                instrumentRepository,
                historyRepository,
                new IndicatorSeriesCalculator(new MovingAverageService(), new RsiService()),
                binancePairMapper,
                FIXED_CLOCK
        );

        List<TechnicalCandleDto> candles = service.getCandles("THYAO", "1m", "1d");

        assertThat(candles).hasSize(1);
        assertThat(candles.getFirst().open()).isEqualByComparingTo("318");
        assertThat(candles.getFirst().high()).isEqualByComparingTo("325");
        assertThat(candles.getFirst().low()).isEqualByComparingTo("315");
        assertThat(candles.getFirst().close()).isEqualByComparingTo("321");
    }

    @Test
    void getCandles_should_filter_incomplete_ohlc_rows() {
        MarketInstrumentRepository instrumentRepository = mock(MarketInstrumentRepository.class);
        MarketPriceHistoryRepository historyRepository = mock(MarketPriceHistoryRepository.class);
        BinancePairMapper binancePairMapper = mock(BinancePairMapper.class);

        MarketInstrument instrument = MarketInstrument.builder()
                .instrumentCode("BTC")
                .instrumentType(InstrumentType.CRYPTO)
                .sourceName(SourceName.BINANCE)
                .build();

        when(binancePairMapper.toDisplayCode("BTC")).thenReturn("BTC");
        when(instrumentRepository.findAllByInstrumentCodeInAndSourceName(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(SourceName.BINANCE)))
                .thenReturn(List.of(instrument));

        MarketPriceHistory complete = MarketPriceHistory.builder()
                .instrument(instrument)
                .intervalType(IntervalType.ONE_DAY)
                .sourceName(SourceName.BINANCE)
                .priceTimestamp(Instant.parse("2026-05-01T00:00:00Z"))
                .openPrice(BigDecimal.valueOf(100))
                .highPrice(BigDecimal.valueOf(110))
                .lowPrice(BigDecimal.valueOf(95))
                .closePrice(BigDecimal.valueOf(105))
                .volume(BigDecimal.valueOf(1000))
                .build();

        MarketPriceHistory incomplete = MarketPriceHistory.builder()
                .instrument(instrument)
                .intervalType(IntervalType.ONE_DAY)
                .sourceName(SourceName.BINANCE)
                .priceTimestamp(Instant.parse("2026-05-02T00:00:00Z"))
                .openPrice(null)
                .highPrice(BigDecimal.valueOf(112))
                .lowPrice(BigDecimal.valueOf(98))
                .closePrice(BigDecimal.valueOf(108))
                .volume(BigDecimal.valueOf(1200))
                .build();

        when(historyRepository.findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                org.mockito.ArgumentMatchers.eq(instrument),
                org.mockito.ArgumentMatchers.eq(IntervalType.ONE_DAY),
                org.mockito.ArgumentMatchers.eq(SourceName.BINANCE),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of(complete, incomplete));

        TechnicalCandleService service = new TechnicalCandleService(
                instrumentRepository,
                historyRepository,
                new IndicatorSeriesCalculator(new MovingAverageService(), new RsiService()),
                binancePairMapper,
                FIXED_CLOCK
        );

        List<TechnicalCandleDto> candles = service.getCandles("BTC", "1m", "1d");

        assertThat(candles).hasSize(1);
        assertThat(candles.getFirst().timestamp()).isEqualTo(complete.getPriceTimestamp().getEpochSecond());
        assertThat(candles.getFirst().close()).isEqualByComparingTo("105");
    }

    @Test
    void getCandles_should_resolve_try_pair_aliases_for_btc_and_eth() {
        MarketInstrumentRepository instrumentRepository = mock(MarketInstrumentRepository.class);
        MarketPriceHistoryRepository historyRepository = mock(MarketPriceHistoryRepository.class);
        BinancePairMapper binancePairMapper = mock(BinancePairMapper.class);

        MarketInstrument btcInstrument = MarketInstrument.builder()
                .instrumentCode("BTCTRY")
                .instrumentType(InstrumentType.CRYPTO)
                .sourceName(SourceName.BINANCE)
                .build();

        MarketInstrument ethInstrument = MarketInstrument.builder()
                .instrumentCode("ETHTRY")
                .instrumentType(InstrumentType.CRYPTO)
                .sourceName(SourceName.BINANCE)
                .build();

        when(binancePairMapper.toDisplayCode("BTC")).thenReturn("BTC");
        when(binancePairMapper.toDisplayCode("ETH")).thenReturn("ETH");
        when(instrumentRepository.findAllByInstrumentCodeInAndSourceName(
                org.mockito.ArgumentMatchers.argThat(list -> list != null && list.contains("BTCTRY")),
                org.mockito.ArgumentMatchers.eq(SourceName.BINANCE)
        )).thenReturn(List.of(btcInstrument));
        when(instrumentRepository.findAllByInstrumentCodeInAndSourceName(
                org.mockito.ArgumentMatchers.argThat(list -> list != null && list.contains("ETHTRY")),
                org.mockito.ArgumentMatchers.eq(SourceName.BINANCE)
        )).thenReturn(List.of(ethInstrument));

        when(historyRepository.findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(IntervalType.ONE_DAY),
                org.mockito.ArgumentMatchers.eq(SourceName.BINANCE),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenAnswer(invocation -> {
            MarketInstrument instrument = invocation.getArgument(0);
            return List.of(MarketPriceHistory.builder()
                    .instrument(instrument)
                    .intervalType(IntervalType.ONE_DAY)
                    .sourceName(SourceName.BINANCE)
                    .priceTimestamp(Instant.parse("2026-05-01T00:00:00Z"))
                    .openPrice(BigDecimal.valueOf(100))
                    .highPrice(BigDecimal.valueOf(110))
                    .lowPrice(BigDecimal.valueOf(95))
                    .closePrice(BigDecimal.valueOf(105))
                    .volume(BigDecimal.valueOf(1000))
                    .build());
        });

        TechnicalCandleService service = new TechnicalCandleService(
                instrumentRepository,
                historyRepository,
                new IndicatorSeriesCalculator(new MovingAverageService(), new RsiService()),
                binancePairMapper,
                FIXED_CLOCK
        );

        assertThat(service.getCandles("BTC", "1m", "1d")).hasSize(1);
        assertThat(service.getCandles("ETH", "1m", "1d")).hasSize(1);
    }

    @Test
    void getCandles_should_deduplicate_duplicate_timestamps_and_keep_ascending_order() {
        MarketInstrumentRepository instrumentRepository = mock(MarketInstrumentRepository.class);
        MarketPriceHistoryRepository historyRepository = mock(MarketPriceHistoryRepository.class);
        BinancePairMapper binancePairMapper = mock(BinancePairMapper.class);

        MarketInstrument instrument = MarketInstrument.builder()
                .id(99L)
                .instrumentCode("BTC")
                .instrumentType(InstrumentType.CRYPTO)
                .sourceName(SourceName.BINANCE)
                .build();

        when(binancePairMapper.toDisplayCode("BTC")).thenReturn("BTC");
        when(instrumentRepository.findAllByInstrumentCodeInAndSourceName(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(SourceName.BINANCE)))
                .thenReturn(List.of(instrument));

        MarketPriceHistory duplicateOld = MarketPriceHistory.builder()
                .instrument(instrument)
                .intervalType(IntervalType.ONE_DAY)
                .sourceName(SourceName.BINANCE)
                .priceTimestamp(Instant.parse("2026-05-01T00:00:00Z"))
                .openPrice(BigDecimal.valueOf(100))
                .highPrice(BigDecimal.valueOf(110))
                .lowPrice(BigDecimal.valueOf(95))
                .closePrice(BigDecimal.valueOf(105))
                .volume(BigDecimal.valueOf(1000))
                .build();

        MarketPriceHistory duplicateNew = MarketPriceHistory.builder()
                .instrument(instrument)
                .intervalType(IntervalType.ONE_DAY)
                .sourceName(SourceName.BINANCE)
                .priceTimestamp(Instant.parse("2026-05-01T00:00:00Z"))
                .openPrice(BigDecimal.valueOf(101))
                .highPrice(BigDecimal.valueOf(111))
                .lowPrice(BigDecimal.valueOf(96))
                .closePrice(BigDecimal.valueOf(106))
                .volume(BigDecimal.valueOf(1001))
                .build();

        MarketPriceHistory secondDay = MarketPriceHistory.builder()
                .instrument(instrument)
                .intervalType(IntervalType.ONE_DAY)
                .sourceName(SourceName.BINANCE)
                .priceTimestamp(Instant.parse("2026-05-02T00:00:00Z"))
                .openPrice(BigDecimal.valueOf(107))
                .highPrice(BigDecimal.valueOf(112))
                .lowPrice(BigDecimal.valueOf(103))
                .closePrice(BigDecimal.valueOf(108))
                .volume(BigDecimal.valueOf(1002))
                .build();

        when(historyRepository.findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                org.mockito.ArgumentMatchers.eq(instrument),
                org.mockito.ArgumentMatchers.eq(IntervalType.ONE_DAY),
                org.mockito.ArgumentMatchers.eq(SourceName.BINANCE),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of(duplicateOld, duplicateNew, secondDay));

        TechnicalCandleService service = new TechnicalCandleService(
                instrumentRepository,
                historyRepository,
                new IndicatorSeriesCalculator(new MovingAverageService(), new RsiService()),
                binancePairMapper,
                FIXED_CLOCK
        );

        List<TechnicalCandleDto> candles = service.getCandles("BTC", "1m", "1d");

        assertThat(candles).hasSize(2);
        assertThat(candles.get(0).timestamp()).isEqualTo(Instant.parse("2026-05-01T00:00:00Z").getEpochSecond());
        assertThat(candles.get(0).close()).isEqualByComparingTo("106");
        assertThat(candles.get(1).timestamp()).isEqualTo(Instant.parse("2026-05-02T00:00:00Z").getEpochSecond());
    }

    @Test
    void getCandles_should_return_null_sma_values_during_warmup_period() {
        MarketInstrumentRepository instrumentRepository = mock(MarketInstrumentRepository.class);
        MarketPriceHistoryRepository historyRepository = mock(MarketPriceHistoryRepository.class);
        BinancePairMapper binancePairMapper = mock(BinancePairMapper.class);

        MarketInstrument instrument = MarketInstrument.builder()
                .instrumentCode("BTC")
                .instrumentType(InstrumentType.CRYPTO)
                .sourceName(SourceName.BINANCE)
                .build();

        when(binancePairMapper.toDisplayCode("BTC")).thenReturn("BTC");
        when(instrumentRepository.findAllByInstrumentCodeInAndSourceName(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(SourceName.BINANCE)))
                .thenReturn(List.of(instrument));

        List<MarketPriceHistory> history = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> MarketPriceHistory.builder()
                        .instrument(instrument)
                        .intervalType(IntervalType.ONE_DAY)
                        .sourceName(SourceName.BINANCE)
                        .priceTimestamp(Instant.parse("2026-05-01T00:00:00Z").plusSeconds(index * 86_400L))
                        .openPrice(BigDecimal.valueOf(100 + index))
                        .highPrice(BigDecimal.valueOf(110 + index))
                        .lowPrice(BigDecimal.valueOf(95 + index))
                        .closePrice(BigDecimal.valueOf(105 + index))
                        .volume(BigDecimal.valueOf(1000 + index))
                        .build())
                .toList();

        when(historyRepository.findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                org.mockito.ArgumentMatchers.eq(instrument),
                org.mockito.ArgumentMatchers.eq(IntervalType.ONE_DAY),
                org.mockito.ArgumentMatchers.eq(SourceName.BINANCE),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(history);

        TechnicalCandleService service = new TechnicalCandleService(
                instrumentRepository,
                historyRepository,
                new IndicatorSeriesCalculator(new MovingAverageService(), new RsiService()),
                binancePairMapper,
                FIXED_CLOCK
        );

        List<TechnicalCandleDto> candles = service.getCandles("BTC", "1m", "1d");

        assertThat(candles).hasSize(8);
        assertThat(candles.subList(0, 6)).allMatch(candle -> candle.sma7() == null);
        assertThat(candles.get(6).sma7()).isEqualByComparingTo("108.00000000");
        assertThat(candles.subList(0, 8)).allMatch(candle -> candle.sma20() == null && candle.sma50() == null);
    }

    @Test
    void getCandles_should_compute_sma50_for_first_in_range_point_using_warmup_history() {
        MarketInstrumentRepository instrumentRepository = mock(MarketInstrumentRepository.class);
        MarketPriceHistoryRepository historyRepository = mock(MarketPriceHistoryRepository.class);
        BinancePairMapper binancePairMapper = mock(BinancePairMapper.class);

        MarketInstrument instrument = MarketInstrument.builder()
                .instrumentCode("BTC")
                .instrumentType(InstrumentType.CRYPTO)
                .sourceName(SourceName.BINANCE)
                .build();

        when(binancePairMapper.toDisplayCode("BTC")).thenReturn("BTC");
        when(instrumentRepository.findAllByInstrumentCodeInAndSourceName(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(SourceName.BINANCE)))
                .thenReturn(List.of(instrument));

        // 2026-03-01'den 2026-05-31'e kesintisiz günlük seri (92 mum). "1m" aralığı (FIXED_CLOCK=2026-06-01)
        // 2026-05-01'de başlar; öncesindeki Mart/Nisan mumları yalnızca warm-up içindir.
        Instant seriesStart = Instant.parse("2026-03-01T00:00:00Z");
        List<MarketPriceHistory> history = java.util.stream.IntStream.range(0, 92)
                .mapToObj(index -> MarketPriceHistory.builder()
                        .instrument(instrument)
                        .intervalType(IntervalType.ONE_DAY)
                        .sourceName(SourceName.BINANCE)
                        .priceTimestamp(seriesStart.plusSeconds(index * 86_400L))
                        .openPrice(BigDecimal.valueOf(100 + index))
                        .highPrice(BigDecimal.valueOf(110 + index))
                        .lowPrice(BigDecimal.valueOf(95 + index))
                        .closePrice(BigDecimal.valueOf(100 + index))
                        .volume(BigDecimal.valueOf(1000 + index))
                        .build())
                .toList();

        when(historyRepository.findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                org.mockito.ArgumentMatchers.eq(instrument),
                org.mockito.ArgumentMatchers.eq(IntervalType.ONE_DAY),
                org.mockito.ArgumentMatchers.eq(SourceName.BINANCE),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(history);

        TechnicalCandleService service = new TechnicalCandleService(
                instrumentRepository,
                historyRepository,
                new IndicatorSeriesCalculator(new MovingAverageService(), new RsiService()),
                binancePairMapper,
                FIXED_CLOCK
        );

        List<TechnicalCandleDto> candles = service.getCandles("BTC", "1m", "1d");

        long displayStartEpoch = Instant.parse("2026-05-01T00:00:00Z").getEpochSecond();
        long displayEndEpoch = Instant.parse("2026-05-31T00:00:00Z").getEpochSecond();

        // Response yalnızca seçilen aralığı (Mayıs) kapsar; warm-up barları (Mart/Nisan) gizli kalır.
        assertThat(candles).hasSize(31);
        assertThat(candles).allMatch(candle -> candle.timestamp() >= displayStartEpoch);
        assertThat(candles.getFirst().timestamp()).isEqualTo(displayStartEpoch);
        assertThat(candles.getLast().timestamp()).isEqualTo(displayEndEpoch);

        // Aralığın ilk gününde (2026-05-01, seride 61. indeks) MA50 warm-up sayesinde hesaplanır:
        // closes[12..61] = 112..161 -> ortalama 136.5.
        assertThat(candles.getFirst().sma50()).isNotNull();
        assertThat(candles.getFirst().sma50()).isEqualByComparingTo("136.5");
    }

    @Test
    void getCandles_should_reject_symbol_with_invalid_characters() {
        TechnicalCandleService service = new TechnicalCandleService(
                mock(MarketInstrumentRepository.class),
                mock(MarketPriceHistoryRepository.class),
                new IndicatorSeriesCalculator(new MovingAverageService(), new RsiService()),
                mock(BinancePairMapper.class),
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.getCandles("BTC<>", "1m", "1d"))
                .isInstanceOf(TechnicalAnalysisException.Validation.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void getCandles_should_reject_symbol_exceeding_max_length() {
        TechnicalCandleService service = new TechnicalCandleService(
                mock(MarketInstrumentRepository.class),
                mock(MarketPriceHistoryRepository.class),
                new IndicatorSeriesCalculator(new MovingAverageService(), new RsiService()),
                mock(BinancePairMapper.class),
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.getCandles("A".repeat(31), "1m", "1d"))
                .isInstanceOf(TechnicalAnalysisException.Validation.class)
                .hasMessageContaining("too long");
    }
}
