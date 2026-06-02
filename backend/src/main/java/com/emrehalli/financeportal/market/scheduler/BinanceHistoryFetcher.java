package com.emrehalli.financeportal.market.scheduler;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.LastHistoryDateProjection;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.market.support.BinancePairMapper;
import com.emrehalli.financeportal.technicalanalysis.service.TechnicalAnalysisCacheEvictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class BinanceHistoryFetcher {

    private final RestTemplate restTemplate;
    private final BinancePairMapper pairMapper;
    private final MarketInstrumentRepository instrumentRepository;
    private final MarketPriceHistoryRepository historyRepository;
    private final MarketProperties binanceProperties;
    private final TechnicalAnalysisCacheEvictionService technicalAnalysisCacheEvictionService;
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    @Scheduled(initialDelay = 2 * 60 * 1000, fixedDelay = Long.MAX_VALUE)
    public void initialLoad() {
        if (!pairMapper.isReady()) {
            log.warn("[BinanceHistoryFetcher] PairMapper hazır değil, initialLoad atlanıyor.");
            return;
        }

        List<String> symbols = pairMapper.getAllSymbols();
        if (symbols.isEmpty()) {
            log.warn("[BinanceHistoryFetcher] getAllSymbols() boş döndü, işlem yapılmıyor.");
            return;
        }

        Map<String, LocalDate> lastDateByCode = preloadLastDates();
        log.info("[BinanceHistoryFetcher] Binance history last-date preload completed. symbolCount={}", lastDateByCode.size());
        log.info("[BinanceHistoryFetcher] Startup catch-up başlıyor. Sembol sayısı: {}", symbols.size());

        List<Future<Integer>> futures = symbols.stream()
                .map(binanceSymbol -> executor.submit(() -> {
                    String coin = pairMapper.toDisplayCode(binanceSymbol);
                    try {
                        int gapDays = resolveGapDays(lastDateByCode.get(coin));
                        if (gapDays == 0) {
                            log.debug("[BinanceHistoryFetcher] Zaten güncel, atlanıyor. coin={}", coin);
                            return 0;
                        }
                        log.info("[BinanceHistoryFetcher] Gap tespit edildi. coin={} gapDays={}", coin, gapDays);
                        return fetchAndSavePage(coin, binanceSymbol, gapDays, null);
                    } catch (Exception e) {
                        log.warn("[BinanceHistoryFetcher] initialLoad coin işlenemedi. coin={} symbol={}", coin, binanceSymbol, e);
                        return 0;
                    }
                }))
                .toList();

        int total = 0;
        for (Future<Integer> future : futures) {
            try {
                total += future.get();
            } catch (Exception e) {
                log.warn("[BinanceHistoryFetcher] Future hatası", e);
            }
        }

        log.info("[BinanceHistoryFetcher] Startup catch-up tamamlandı. Toplam kaydedilen: {}", total);
        if (total > 0) {
            technicalAnalysisCacheEvictionService.evictAllTechnicalCaches();
        }
    }

    private Map<String, LocalDate> preloadLastDates() {
        return historyRepository
                .findLastDatePerInstrument(InstrumentType.CRYPTO, SourceName.BINANCE, IntervalType.ONE_DAY)
                .stream()
                .filter(p -> p.getCode() != null && p.getLastTimestamp() != null)
                .collect(Collectors.toMap(
                        p -> p.getCode().trim().toUpperCase(),
                        p -> p.getLastTimestamp().atZone(ZoneOffset.UTC).toLocalDate()
                ));
    }

    private int resolveGapDays(LocalDate lastDate) {
        if (lastDate == null) return 90;
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        if (!lastDate.isBefore(yesterday)) return 0;
        return (int) Math.min(ChronoUnit.DAYS.between(lastDate, yesterday) + 2, 90);
    }

    @Scheduled(cron = "0 30 0 * * *", zone = "UTC")
    @Transactional
    public void dailyFetch() {
        if (!pairMapper.isReady()) {
            log.warn("[BinanceHistoryFetcher] PairMapper hazÄ±r deÄŸil, dailyFetch atlanÄ±yor.");
            return;
        }
        int totalSaved = 0;
        for (String binanceSymbol : pairMapper.getAllSymbols()) {
            String coin = pairMapper.toDisplayCode(binanceSymbol);
            try {
                totalSaved += fetchAndSavePage(coin, binanceSymbol, 1, null);
            } catch (Exception exception) {
                log.warn("[BinanceHistoryFetcher] dailyFetch coin iÅŸlenemedi. coin={}, symbol={}", coin, binanceSymbol, exception);
            }
        }
        if (totalSaved > 0) {
            technicalAnalysisCacheEvictionService.evictAllTechnicalCaches();
        }
    }

    @SuppressWarnings("unchecked")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private int fetchAndSavePage(String coin, String binanceSymbol, int limit, Long endTime) {
        if (binanceSymbol == null || binanceSymbol.isBlank()) {
            log.warn("[BinanceHistoryFetcher] Binance sembolÃ¼ boÅŸ, atlanÄ±yor. coin={}", coin);
            return 0;
        }

        String url = binanceProperties.getProviders().getBinance().getBaseUrl()
                + "/klines"
                + "?symbol=" + binanceSymbol
                + "&interval=1d"
                + "&limit=" + limit
                + (endTime != null ? "&endTime=" + endTime : "");

        List<List<Object>> klines = restTemplate.getForObject(url, List.class);
        if (klines == null || klines.isEmpty()) {
            log.warn("[BinanceHistoryFetcher] Binance kline verisi boÅŸ dÃ¶ndÃ¼. coin={}, symbol={}, limit={}",
                    coin, binanceSymbol, limit);
            return 0;
        }

        MarketInstrument instrument = findOrCreateInstrument(coin);
        int savedCount = 0;
        for (List<Object> kline : klines) {
            try {
                if (kline == null || kline.size() < 6) {
                    log.warn("[BinanceHistoryFetcher] Eksik kline verisi atlandÄ±. coin={}, symbol={}", coin, binanceSymbol);
                    continue;
                }

                long openTime = ((Number) kline.get(0)).longValue();
                Instant priceTimestamp = Instant.ofEpochMilli(openTime).truncatedTo(ChronoUnit.DAYS);
                BigDecimal openPrice = new BigDecimal(kline.get(1).toString());
                BigDecimal highPrice = new BigDecimal(kline.get(2).toString());
                BigDecimal lowPrice = new BigDecimal(kline.get(3).toString());
                BigDecimal closePrice = new BigDecimal(kline.get(4).toString());
                BigDecimal volume = new BigDecimal(kline.get(5).toString());

                boolean exists = historyRepository.existsByInstrumentAndIntervalTypeAndPriceTimestamp(
                        instrument,
                        IntervalType.ONE_DAY,
                        priceTimestamp
                );
                if (exists) {
                    continue;
                }

                historyRepository.save(MarketPriceHistory.builder()
                        .instrument(instrument)
                        .intervalType(IntervalType.ONE_DAY)
                        .priceTimestamp(priceTimestamp)
                        .openPrice(openPrice)
                        .highPrice(highPrice)
                        .lowPrice(lowPrice)
                        .closePrice(closePrice)
                        .volume(volume)
                        .sourceName(SourceName.BINANCE)
                        .build());
                savedCount++;
            } catch (Exception exception) {
                log.warn("[BinanceHistoryFetcher] Kline parse/save hatasÄ±. coin={}, symbol={}, rawKline={}",
                        coin, binanceSymbol, kline, exception);
            }
        }
        return savedCount;
    }

    @Transactional
    public int fetchHistoryManual(int days) {
        if (!pairMapper.isReady()) {
            log.warn("[BinanceHistoryFetcher] PairMapper hazÄ±r deÄŸil.");
            return 0;
        }

        int pages = (int) Math.ceil((double) days / 1000);
        List<String> allSymbols = pairMapper.getAllSymbols();
        AtomicInteger totalSaved = new AtomicInteger(0);

        List<Future<Integer>> futures = allSymbols.stream()
                .map(binanceSymbol -> executor.submit(() -> {
                    String coin = pairMapper.toDisplayCode(binanceSymbol);
                    int coinSaved = 0;
                    Long endTime = null;

                    for (int page = 0; page < pages; page++) {
                        try {
                            int saved = fetchAndSavePage(coin, binanceSymbol, 1000, endTime);
                            coinSaved += saved;

                            if (saved == 0) {
                                break;
                            }

                            Optional<MarketPriceHistory> oldest = historyRepository
                                    .findTopByInstrumentAndIntervalTypeAndSourceNameOrderByPriceTimestampAsc(
                                            findOrCreateInstrument(coin),
                                            IntervalType.ONE_DAY,
                                            SourceName.BINANCE
                                    );
                            if (oldest.isEmpty()) {
                                break;
                            }
                            endTime = oldest.get().getPriceTimestamp().toEpochMilli();
                        } catch (Exception ex) {
                            log.warn("[BinanceHistoryFetcher] Hata. coin={}", coin, ex);
                            break;
                        }
                    }
                    return coinSaved;
                }))
                .toList();

        for (Future<Integer> future : futures) {
            try {
                totalSaved.addAndGet(future.get());
            } catch (Exception ex) {
                log.warn("[BinanceHistoryFetcher] Future hatasÄ±", ex);
            }
        }

        int savedTotal = totalSaved.get();
        log.info("[BinanceHistoryFetcher] Manuel yukleme tamamlandi. Toplam: {}", savedTotal);
        if (savedTotal > 0) {
            technicalAnalysisCacheEvictionService.evictAllTechnicalCaches();
        }
        return savedTotal;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private MarketInstrument findOrCreateInstrument(String coin) {
        String normalizedCoin = coin == null ? "" : coin.trim().toUpperCase();
        return instrumentRepository.findByInstrumentCodeIgnoreCaseAndSourceName(normalizedCoin, SourceName.BINANCE)
                .filter(instrument -> instrument.getInstrumentType() == InstrumentType.CRYPTO)
                .orElseGet(() -> {
                    MarketInstrument marketInstrument = new MarketInstrument();
                    marketInstrument.setInstrumentCode(normalizedCoin);
                    marketInstrument.setInstrumentName(normalizedCoin + "/TRY");
                    marketInstrument.setInstrumentType(InstrumentType.CRYPTO);
                    marketInstrument.setSourceName(SourceName.BINANCE);
                    return instrumentRepository.save(marketInstrument);
                });
    }
}





