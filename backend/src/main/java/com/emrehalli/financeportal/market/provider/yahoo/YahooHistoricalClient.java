package com.emrehalli.financeportal.market.provider.yahoo;

import com.emrehalli.financeportal.common.logging.SensitiveDataMasker;
import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.stock.dto.StockHistoryDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches ONE_DAY OHLCV history from Yahoo Finance's v8/finance/chart endpoint.
 * Cookie and crumb are managed automatically by {@link YahooFinanceSessionService}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class YahooHistoricalClient {

    private final MarketProperties props;
    private final YahooFinanceSessionService yahooSessionService;
    private final ObjectMapper objectMapper;

    @CircuitBreaker(name = "yahooFinance", fallbackMethod = "fetchDailyHistoryFallback")
    public List<StockHistoryDto> fetchDailyHistory(String yahooSymbol, int days) {
        long period2 = Instant.now().getEpochSecond();
        long period1 = period2 - ((long) days * 86400);
        return fetchDailyHistory(yahooSymbol, period1, period2);
    }

    @CircuitBreaker(name = "yahooFinance", fallbackMethod = "fetchDailyHistoryRangeFallback")
    public List<StockHistoryDto> fetchDailyHistory(String yahooSymbol, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new DataProviderException("Invalid Yahoo historical date range. symbol=" + yahooSymbol);
        }

        long period1 = startDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long period2 = endDate.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        return fetchDailyHistory(yahooSymbol, period1, period2);
    }

    private List<StockHistoryDto> fetchDailyHistory(String yahooSymbol, long period1, long period2) {
        String baseUrl = props.getProviders().getYahoo().getBaseUrl();
        String url = baseUrl + "/v8/finance/chart/" + yahooSymbol
                + "?interval=1d&period1=" + period1 + "&period2=" + period2;

        try {
            log.debug("Yahoo historical request. symbol={} maskedUrl={}",
                    yahooSymbol, SensitiveDataMasker.maskUri(url));

            ResponseEntity<String> response = yahooSessionService.exchangeWithSession(url);

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                throw new DataProviderException("Yahoo historical returned empty payload. symbol=" + yahooSymbol);
            }

            List<StockHistoryDto> bars = parse(body, yahooSymbol);
            log.info("Yahoo historical fetch completed. symbol={} bars={}", yahooSymbol, bars.size());
            return bars;

        } catch (HttpStatusCodeException e) {
            throw new DataProviderException("Yahoo historical HTTP " + e.getStatusCode().value()
                    + " for symbol=" + yahooSymbol, e);
        } catch (DataProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new DataProviderException("Failed to parse Yahoo historical payload. symbol=" + yahooSymbol, e);
        }
    }

    private List<StockHistoryDto> fetchDailyHistoryFallback(String yahooSymbol, int days, Throwable throwable) {
        log.warn("Yahoo Finance circuit breaker fallback triggered, returning empty history. symbol={}, reason={}",
                yahooSymbol, throwable.toString());
        return List.of();
    }

    private List<StockHistoryDto> fetchDailyHistoryRangeFallback(String yahooSymbol, LocalDate startDate, LocalDate endDate, Throwable throwable) {
        log.warn("Yahoo Finance circuit breaker fallback triggered, returning empty history. symbol={}, reason={}",
                yahooSymbol, throwable.toString());
        return List.of();
    }

    private List<StockHistoryDto> parse(String body, String yahooSymbol) throws Exception {
        JsonNode result = objectMapper.readTree(body)
                .path("chart")
                .path("result");

        if (!result.isArray() || result.isEmpty()) {
            log.warn("Yahoo historical: no result array. symbol={}", yahooSymbol);
            return List.of();
        }

        JsonNode item = result.get(0);
        JsonNode timestamps = item.path("timestamp");
        JsonNode quoteArray = item.path("indicators").path("quote");

        if (!timestamps.isArray() || !quoteArray.isArray() || quoteArray.isEmpty()) {
            log.warn("Yahoo historical: missing timestamps or quote array. symbol={}", yahooSymbol);
            return List.of();
        }

        JsonNode q = quoteArray.get(0);
        JsonNode opens = q.path("open");
        JsonNode highs = q.path("high");
        JsonNode lows = q.path("low");
        JsonNode closes = q.path("close");
        JsonNode volumes = q.path("volume");

        List<StockHistoryDto> bars = new ArrayList<>();
        for (int i = 0; i < timestamps.size(); i++) {
            BigDecimal close = decimal(closes, i);
            if (close == null || close.signum() <= 0) {
                continue;
            }

            Instant ts = Instant.ofEpochSecond(timestamps.get(i).longValue())
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                    .atStartOfDay()
                    .toInstant(ZoneOffset.UTC);

            BigDecimal volume = (volumes.isArray() && i < volumes.size() && !volumes.get(i).isNull())
                    ? BigDecimal.valueOf(volumes.get(i).longValue()) : null;

            bars.add(new StockHistoryDto(ts,
                    decimal(opens, i),
                    decimal(highs, i),
                    decimal(lows, i),
                    close,
                    volume));
        }
        return bars;
    }

    private BigDecimal decimal(JsonNode array, int i) {
        if (!array.isArray() || i >= array.size() || array.get(i).isNull()) {
            return null;
        }
        try {
            return new BigDecimal(array.get(i).asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
