package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.market.api.dto.MarketHistoryEnvelopeResponse;
import com.emrehalli.financeportal.market.api.dto.MarketHistoryDebugResponse;
import com.emrehalli.financeportal.market.api.dto.MarketHistoryResponse;
import com.emrehalli.financeportal.market.api.mapper.MarketApiMapper;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.service.MarketHistoryBackfillProperties;
import com.emrehalli.financeportal.market.service.MarketHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/markets")
public class MarketHistoryController {

    private static final Logger log = LoggerFactory.getLogger(MarketHistoryController.class);

    private final MarketHistoryService marketHistoryService;
    private final MarketApiMapper marketApiMapper;
    private final Clock clock;
    private final MarketHistoryBackfillProperties backfillProperties;
    private final AppMessageSource appMessageSource;

    public MarketHistoryController(MarketHistoryService marketHistoryService,
                                   MarketApiMapper marketApiMapper,
                                   Clock clock,
                                   MarketHistoryBackfillProperties backfillProperties,
                                   AppMessageSource appMessageSource) {
        this.marketHistoryService = marketHistoryService;
        this.marketApiMapper = marketApiMapper;
        this.clock = clock;
        this.backfillProperties = backfillProperties;
        this.appMessageSource = appMessageSource;
    }

    @GetMapping("/{symbol}/history")
    public MarketHistoryEnvelopeResponse getHistory(
            @PathVariable String symbol,
            @RequestParam(required = false) DataSource source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String range
    ) {
        DateRange resolvedRange = resolveDateRange(symbol, startDate, endDate, range);
        List<com.emrehalli.financeportal.market.service.model.MarketHistoryRecord> history = marketHistoryService.getHistory(
                symbol,
                source,
                resolvedRange.startDate(),
                resolvedRange.endDate()
        );
        logHistoryResult(symbol, source, resolvedRange, history);
        List<MarketHistoryResponse> data = history.stream()
                .map(marketApiMapper::toHistoryResponse)
                .toList();
        int requiredPointCount = Math.max(backfillProperties.getRequiredHistoryPointCount(), 1);
        if (data.size() < requiredPointCount) {
            return new MarketHistoryEnvelopeResponse(
                    "INSUFFICIENT_HISTORY",
                    data.size(),
                    requiredPointCount,
                    source == null ? null : source.name(),
                    appMessageSource.get("market.history.insufficient"),
                    List.of()
            );
        }
        return new MarketHistoryEnvelopeResponse(
                "AVAILABLE",
                data.size(),
                requiredPointCount,
                source == null ? null : source.name(),
                null,
                data
        );
    }

    @GetMapping("/{symbol}/history/debug")
    public MarketHistoryDebugResponse getHistoryDebug(
            @PathVariable String symbol,
            @RequestParam(required = false) DataSource source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String range
    ) {
        DateRange resolvedRange = resolveDateRange(symbol, startDate, endDate, range);
        List<com.emrehalli.financeportal.market.service.model.MarketHistoryRecord> history = marketHistoryService.getHistory(
                symbol,
                source,
                resolvedRange.startDate(),
                resolvedRange.endDate()
        );
        logHistoryResult(symbol, source, resolvedRange, history);
        return new MarketHistoryDebugResponse(
                symbol,
                resolvedRange.requestedRange(),
                resolvedRange.startDate(),
                resolvedRange.endDate(),
                history.size(),
                history.stream().map(com.emrehalli.financeportal.market.service.model.MarketHistoryRecord::priceDate).min(LocalDate::compareTo).orElse(null),
                history.stream().map(com.emrehalli.financeportal.market.service.model.MarketHistoryRecord::priceDate).max(LocalDate::compareTo).orElse(null),
                history.stream().map(com.emrehalli.financeportal.market.service.model.MarketHistoryRecord::closePrice).filter(java.util.Objects::nonNull).distinct().count(),
                source == null ? null : source.name()
        );
    }

    private DateRange resolveDateRange(String symbol, LocalDate startDate, LocalDate endDate, String range) {
        if (startDate != null && endDate != null) {
            log.info("Market history range resolved: symbol={}, requestedRange={}, resolvedStartDate={}, resolvedEndDate={}", symbol, range, startDate, endDate);
            return new DateRange(startDate, endDate, range);
        }

        if (startDate != null || endDate != null) {
            throw new BadRequestException("startDate and endDate must be provided together or use range");
        }

        String effectiveRange = (range == null || range.isBlank()) ? "1m" : range.trim().toLowerCase();
        LocalDate today = LocalDate.now(clock);
        LocalDate resolvedStartDate = switch (effectiveRange) {
            case "7d" -> today.minusDays(7);
            case "1m" -> today.minusMonths(1);
            case "3m" -> today.minusMonths(3);
            case "1y" -> today.minusYears(1);
            default -> throw new BadRequestException(appMessageSource.get("market.invalidRange", range));
        };

        log.info("Market history range resolved: symbol={}, requestedRange={}, resolvedStartDate={}, resolvedEndDate={}", symbol, effectiveRange, resolvedStartDate, today);
        return new DateRange(resolvedStartDate, today, effectiveRange);
    }

    private void logHistoryResult(String symbol,
                                  DataSource source,
                                  DateRange resolvedRange,
                                  List<com.emrehalli.financeportal.market.service.model.MarketHistoryRecord> history) {
        LocalDate minReturnedDate = history.stream()
                .map(com.emrehalli.financeportal.market.service.model.MarketHistoryRecord::priceDate)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate maxReturnedDate = history.stream()
                .map(com.emrehalli.financeportal.market.service.model.MarketHistoryRecord::priceDate)
                .max(LocalDate::compareTo)
                .orElse(null);
        long distinctPriceCount = history.stream()
                .map(com.emrehalli.financeportal.market.service.model.MarketHistoryRecord::closePrice)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();

        log.info(
                "Market history endpoint completed: symbol={}, source={}, requestedRange={}, resolvedStartDate={}, resolvedEndDate={}, dbReturnedCount={}, minReturnedDate={}, maxReturnedDate={}, distinctPriceCount={}",
                symbol,
                source,
                resolvedRange.requestedRange(),
                resolvedRange.startDate(),
                resolvedRange.endDate(),
                history.size(),
                minReturnedDate,
                maxReturnedDate,
                distinctPriceCount
        );
    }

    private record DateRange(LocalDate startDate, LocalDate endDate, String requestedRange) {
    }
}
