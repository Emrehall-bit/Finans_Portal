package com.emrehalli.financeportal.market.provider.yahoo;

import com.emrehalli.financeportal.common.logging.SensitiveDataMasker;
import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.stock.dto.StockHistoryDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches ONE_DAY OHLCV history from Yahoo Finance's v8/finance/chart endpoint.
 * Reuses the same cookie/crumb as {@link YahooFinanceQuoteClient}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class YahooHistoricalClient {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

    private final MarketProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * @param yahooSymbol Yahoo Finance ticker, e.g. "GC=F", "SI=F"
     * @param days        look-back window in calendar days
     * @return list of daily bars, earliest first; empty on no data
     * @throws DataProviderException on HTTP or parse failure
     */
    public List<StockHistoryDto> fetchDailyHistory(String yahooSymbol, int days) {
        String cookie = props.getProviders().getYahoo().getCookie();
        String crumb  = props.getProviders().getYahoo().getCrumb();
        if (!hasText(cookie) || !hasText(crumb)) {
            throw new DataProviderException("Yahoo Finance cookie/crumb is not configured");
        }

        long period2 = Instant.now().getEpochSecond();
        long period1 = period2 - ((long) days * 86400);

        // Build URL via string concat to avoid UriComponentsBuilder misinterpreting '=' in ticker path
        String baseUrl = props.getProviders().getYahoo().getBaseUrl();
        String url = baseUrl + "/v8/finance/chart/" + yahooSymbol
                + "?interval=1d&period1=" + period1 + "&period2=" + period2 + "&crumb=" + crumb;

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set(HttpHeaders.COOKIE, cookie);

        try {
            log.debug("Yahoo historical request. symbol={} maskedUrl={}",
                    yahooSymbol, SensitiveDataMasker.maskUri(url));

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                throw new DataProviderException("Yahoo historical returned empty payload. symbol=" + yahooSymbol);
            }

            List<StockHistoryDto> bars = parse(body, yahooSymbol);
            log.info("Yahoo historical fetch completed. symbol={} bars={}", yahooSymbol, bars.size());
            return bars;

        } catch (HttpClientErrorException.Unauthorized e) {
            throw new DataProviderException("Yahoo Finance cookie expired", e);
        } catch (HttpStatusCodeException e) {
            throw new DataProviderException("Yahoo historical HTTP " + e.getStatusCode().value()
                    + " for symbol=" + yahooSymbol, e);
        } catch (DataProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new DataProviderException("Failed to parse Yahoo historical payload. symbol=" + yahooSymbol, e);
        }
    }

    // ── Parsing ────────────────────────────────────────────────────────────────

    private List<StockHistoryDto> parse(String body, String yahooSymbol) throws Exception {
        JsonNode result = objectMapper.readTree(body)
                .path("chart")
                .path("result");

        if (!result.isArray() || result.isEmpty()) {
            log.warn("Yahoo historical: no result array. symbol={}", yahooSymbol);
            return List.of();
        }

        JsonNode item       = result.get(0);
        JsonNode timestamps = item.path("timestamp");
        JsonNode quoteArray = item.path("indicators").path("quote");

        if (!timestamps.isArray() || !quoteArray.isArray() || quoteArray.isEmpty()) {
            log.warn("Yahoo historical: missing timestamps or quote array. symbol={}", yahooSymbol);
            return List.of();
        }

        JsonNode q       = quoteArray.get(0);
        JsonNode opens   = q.path("open");
        JsonNode highs   = q.path("high");
        JsonNode lows    = q.path("low");
        JsonNode closes  = q.path("close");
        JsonNode volumes = q.path("volume");

        List<StockHistoryDto> bars = new ArrayList<>();
        for (int i = 0; i < timestamps.size(); i++) {
            BigDecimal close = decimal(closes, i);
            if (close == null || close.signum() <= 0) {
                continue;
            }

            // Normalise to midnight UTC so duplicate check aligns with CommodityHistoryDerivationService
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
        if (!array.isArray() || i >= array.size() || array.get(i).isNull()) return null;
        try {
            return new BigDecimal(array.get(i).asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
