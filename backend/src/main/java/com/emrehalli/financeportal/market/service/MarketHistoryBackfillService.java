package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.provider.evdsmacro.config.EvdsMacroProperties;
import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import com.emrehalli.financeportal.market.service.model.BackfillRunStatus;
import com.emrehalli.financeportal.market.service.model.MarketBackfillJobResult;
import com.emrehalli.financeportal.market.service.model.MarketHistoryPersistenceResult;
import com.emrehalli.financeportal.market.service.model.MarketRefreshResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class MarketHistoryBackfillService {

    private static final Logger log = LoggerFactory.getLogger(MarketHistoryBackfillService.class);
    private static final int BACKFILL_CHUNK_DAYS = 90;
    private static final long BACKFILL_CHUNK_DELAY_MS = 500L;

    private final ProviderOrchestrationService providerOrchestrationService;
    private final InstrumentRegistryService instrumentRegistryService;
    private final MarketHistoryService marketHistoryService;
    private final EvdsProperties evdsProperties;
    private final EvdsMacroProperties evdsMacroProperties;
    private final MarketHistoryBackfillProperties properties;
    private final MarketBackfillStatusService marketBackfillStatusService;
    private final Clock clock;

    private volatile boolean runtimeStarted;
    private volatile Instant startupAt;

    MarketHistoryBackfillService(ProviderOrchestrationService providerOrchestrationService,
                                 InstrumentRegistryService instrumentRegistryService,
                                 MarketHistoryService marketHistoryService,
                                 EvdsProperties evdsProperties,
                                 MarketHistoryBackfillProperties properties,
                                 MarketBackfillStatusService marketBackfillStatusService,
                                 Clock clock) {
        this(
                providerOrchestrationService,
                instrumentRegistryService,
                marketHistoryService,
                evdsProperties,
                new EvdsMacroProperties(),
                properties,
                marketBackfillStatusService,
                clock
        );
    }

    @Autowired
    public MarketHistoryBackfillService(ProviderOrchestrationService providerOrchestrationService,
                                        InstrumentRegistryService instrumentRegistryService,
                                        MarketHistoryService marketHistoryService,
                                        EvdsProperties evdsProperties,
                                        EvdsMacroProperties evdsMacroProperties,
                                        MarketHistoryBackfillProperties properties,
                                        MarketBackfillStatusService marketBackfillStatusService,
                                        Clock clock) {
        this.providerOrchestrationService = providerOrchestrationService;
        this.instrumentRegistryService = instrumentRegistryService;
        this.marketHistoryService = marketHistoryService;
        this.evdsProperties = evdsProperties;
        this.evdsMacroProperties = evdsMacroProperties;
        this.properties = properties;
        this.marketBackfillStatusService = marketBackfillStatusService;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void markStarted() {
        runtimeStarted = true;
        startupAt = clock.instant();
    }

    public void runIfDue() {
        if (!properties.isEnabled() || !runtimeStarted || startupAt == null) {
            return;
        }
        if (clock.instant().isBefore(startupAt.plusSeconds(Math.max(properties.getStartupDelaySeconds(), 0L)))) {
            return;
        }
        backfillMissingHistory(false);
    }

    public MarketBackfillJobResult triggerManual(DataSource source, String symbol) {
        return backfillSymbol(source, symbol, true);
    }

    private void backfillMissingHistory(boolean force) {
        int minimumHistoryCount = Math.max(properties.getMinDataPoints(), 1);
        Duration cooldown = Duration.ofMinutes(Math.max(properties.getFailedCooldownMinutes(), 1L));

        for (DataSource source : providerOrchestrationService.availableSources()) {
            List<InstrumentRegistryService.ResolvedMapping> mappings = instrumentRegistryService.resolveMappings(source).mappings();
            for (InstrumentRegistryService.ResolvedMapping mapping : mappings) {
                long historyCount = marketHistoryService.countHistory(mapping.symbol(), source);
                if (!marketBackfillStatusService.isEligible(source, mapping.symbol(), (int) historyCount, minimumHistoryCount, cooldown, force)) {
                    continue;
                }
                backfillSymbol(source, mapping.symbol(), false);
            }
        }
    }

    private MarketBackfillJobResult backfillSymbol(DataSource source, String symbol, boolean force) {
        try {
            log.info("marketBackfillJobStarted providerSource={}, symbol={}", source, symbol);
            LocalDate endDate = LocalDate.now(clock);
            LocalDate startDate = instrumentRegistryService.findPreferredMapping(source, symbol)
                    .map(InstrumentRegistryService.ResolvedMapping::historyStartDate)
                    .orElse(null);
            if (startDate == null) {
                int lookbackDays = Math.max(properties.getDefaultLookbackDays(), resolveLookbackDays(source, null));
                startDate = endDate.minusDays(Math.max(lookbackDays - 1L, 0L));
            }

            List<DateChunk> chunks = partition(startDate, endDate);
            marketBackfillStatusService.markRunning(source, symbol, chunks.size());

            int fetchedCount = 0;
            int savedCount = 0;
            int completedChunks = 0;

            for (DateChunk chunk : chunks) {
                try {
                    List<MarketHistoryPersistenceResult> results = backfill(
                            source,
                            List.of(symbol),
                            chunk.startDate(),
                            chunk.endDate()
                    );
                    if (results.isEmpty()) {
                        log.warn("marketBackfillChunkFailed providerSource={}, symbol={}, chunkStart={}, chunkEnd={}, error={}",
                                source, symbol, chunk.startDate(), chunk.endDate(), "No history persisted");
                    } else {
                        fetchedCount += results.stream().mapToInt(MarketHistoryPersistenceResult::received).sum();
                        savedCount += results.stream().mapToInt(MarketHistoryPersistenceResult::saved).sum();
                        completedChunks++;
                        marketBackfillStatusService.updateProgress(source, symbol, chunks.size(), completedChunks, chunk.endDate());
                    }
                } catch (Exception ex) {
                    log.warn("marketBackfillChunkFailed providerSource={}, symbol={}, chunkStart={}, chunkEnd={}, error={}",
                            source, symbol, chunk.startDate(), chunk.endDate(), ex.getMessage(), ex);
                }

                pauseBetweenChunks();
            }

            long finalCount = marketHistoryService.countHistory(symbol, source);
            LocalDate minDate = marketHistoryService.findMinPriceDate(symbol, source);
            LocalDate maxDate = marketHistoryService.findMaxPriceDate(symbol, source);
            long distinctPriceCount = marketHistoryService.countDistinctClosePrices(symbol, source);
            log.info("marketBackfillCompleted providerSource={}, symbol={}, fetchedCount={}, savedCount={}", source, symbol, fetchedCount, savedCount);
            log.info("Market backfill DB stats: providerSource={}, symbol={}, count={}, minPriceDate={}, maxPriceDate={}, distinctPriceCount={}",
                    source, symbol, finalCount, minDate, maxDate, distinctPriceCount);
            BackfillRunStatus status = savedCount > 0 || finalCount >= properties.getRequiredHistoryPointCount()
                    ? BackfillRunStatus.SUCCESS
                    : BackfillRunStatus.FAILED;
            return marketBackfillStatusService.markCompleted(source, symbol, status, fetchedCount, savedCount, minDate, maxDate,
                    status == BackfillRunStatus.SUCCESS ? "Backfill completed" : "Insufficient history after backfill");
        } catch (Exception ex) {
            log.warn("marketBackfillFailed providerSource={}, symbol={}, error={}", source, symbol, ex.getMessage(), ex);
            return marketBackfillStatusService.markCompleted(source, symbol, BackfillRunStatus.FAILED, 0, 0, null, null, ex.getMessage());
        }
    }

    /**
     * Backfills history for all mappings of a provider using a lookback window.
     *
     * @param source provider source
     * @param days lookback days override
     * @return persistence results
     */
    public List<MarketHistoryPersistenceResult> backfill(DataSource source, Integer days) {
        return backfill(source, List.of(), days);
    }

    /**
     * Backfills history for the given provider symbols using a lookback window.
     *
     * @param source provider source
     * @param symbols canonical symbols
     * @param days lookback days override
     * @return persistence results
     */
    public List<MarketHistoryPersistenceResult> backfill(DataSource source, List<String> symbols, Integer days) {
        int lookbackDays = resolveLookbackDays(source, days);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(Math.max(lookbackDays - 1L, 0L));
        return backfill(source, symbols, startDate, endDate);
    }

    /**
     * Backfills history for the given provider symbols and explicit date range.
     *
     * @param source provider source
     * @param symbols canonical symbols
     * @param startDate history start date
     * @param endDate history end date
     * @return persistence results
     */
    public List<MarketHistoryPersistenceResult> backfill(DataSource source,
                                                         List<String> symbols,
                                                         LocalDate startDate,
                                                         LocalDate endDate) {

        log.info(
                "Market history backfill started: source={}, startDate={}, endDate={}, symbolCount={}",
                source,
                startDate,
                endDate,
                symbols == null ? 0 : symbols.size()
        );

        List<MarketRefreshResult> results = providerOrchestrationService.fetchQuoteResults(
                new ProviderFetchRequest(
                        source,
                        symbols == null ? List.of() : symbols,
                        java.util.Set.of(),
                        startDate,
                        endDate,
                        java.util.Map.of()
                )
        );

        List<MarketHistoryPersistenceResult> persistenceResults = results.stream()
                .filter(MarketRefreshResult::success)
                .map(result -> marketHistoryService.persistHistory(result.source(), result.historyRecords()))
                .toList();

        log.info(
                "Market history backfill completed: source={}, startDate={}, endDate={}, symbolCount={}",
                source,
                startDate,
                endDate,
                symbols == null ? 0 : symbols.size()
        );
        return persistenceResults;
    }

    /**
     * Resolves default lookback days for a provider.
     *
     * @param source provider source
     * @param requestedDays explicit override
     * @return effective lookback days
     */
    public int resolveLookbackDays(DataSource source, Integer requestedDays) {
        if (requestedDays != null && requestedDays > 0) {
            return requestedDays;
        }

        if (source == DataSource.EVDS) {
            return Math.max(evdsProperties.getHistory().getBackfillDefaultDays(), 1);
        }

        if (source == DataSource.EVDS_MACRO) {
            return Math.max(evdsMacroProperties.getHistory().getBackfillDefaultDays(), 1);
        }

        return 365;
    }

    List<DateChunk> partition(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return List.of();
        }

        List<DateChunk> chunks = new ArrayList<>();
        LocalDate chunkStart = startDate;
        while (!chunkStart.isAfter(endDate)) {
            LocalDate chunkEnd = chunkStart.plusDays(BACKFILL_CHUNK_DAYS - 1L);
            if (chunkEnd.isAfter(endDate)) {
                chunkEnd = endDate;
            }
            chunks.add(new DateChunk(chunkStart, chunkEnd));
            chunkStart = chunkEnd.plusDays(1);
        }
        return chunks;
    }

    void pauseBetweenChunks() {
        try {
            Thread.sleep(BACKFILL_CHUNK_DELAY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Backfill chunk pause interrupted", ex);
        }
    }

    record DateChunk(LocalDate startDate, LocalDate endDate) {
        long sizeInDays() {
            return ChronoUnit.DAYS.between(startDate, endDate) + 1L;
        }
    }
}
