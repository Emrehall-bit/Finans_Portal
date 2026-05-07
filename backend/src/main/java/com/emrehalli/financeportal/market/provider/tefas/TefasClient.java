package com.emrehalli.financeportal.market.provider.tefas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.ProviderFetchRequest;
import com.emrehalli.financeportal.market.provider.ProviderFetchResult;
import com.emrehalli.financeportal.market.provider.tefas.config.TefasProperties;
import com.emrehalli.financeportal.market.service.InstrumentRegistryService;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TefasClient implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(TefasClient.class);
    private static final DateTimeFormatter TEFAS_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final RestTemplate restTemplate;
    private final TefasProperties properties;
    private final InstrumentRegistryService instrumentRegistryService;
    private final SymbolNormalizer symbolNormalizer;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private TokenBucket tokenBucket;

    @PostConstruct
    void initializeTokenBucket() {
        this.tokenBucket = new TokenBucket(Math.max(properties.getRateLimitPerMinute(), 1), clock);
    }

    @Override
    public DataSource source() {
        return DataSource.TEFAS;
    }

    @Override
    public boolean supports(ProviderFetchRequest request) {
        if (!properties.isEnabled()) {
            return false;
        }
        if (request == null) {
            return true;
        }
        if (request.hasSourceFilter() && request.source() != DataSource.TEFAS) {
            return false;
        }
        return !request.hasInstrumentTypeFilter() || request.instrumentTypes().contains(InstrumentType.FUND);
    }

    @Override
    public ProviderFetchResult fetch(ProviderFetchRequest request) {
        List<InstrumentRegistryService.ResolvedMapping> mappings = resolveMappings(request);
        if (mappings.isEmpty()) {
            log.info("TEFAS provider fetch skipped: no matching fund mappings");
            return new ProviderFetchResult(List.of(), List.of());
        }

        if (isHistoryRequest(request)) {
            List<MarketHistoryRecord> historyRecords = fetchHistoryRecords(mappings, request.from(), request.to());
            return new ProviderFetchResult(List.of(), historyRecords);
        }

        List<MarketQuote> quotes = new ArrayList<>();
        List<MarketHistoryRecord> historyRecords = new ArrayList<>();
        for (InstrumentRegistryService.ResolvedMapping mapping : mappings) {
            TefasPoint currentPoint = fetchCurrentPoint(mapping.providerSymbol());
            if (currentPoint == null) {
                continue;
            }
            TefasPoint previousPoint = fetchPreviousPoint(mapping.providerSymbol(), currentPoint.priceDate());
            quotes.add(toQuote(mapping, currentPoint, previousPoint));
            historyRecords.add(toHistoryRecord(mapping, currentPoint));
        }

        log.info(
                "TEFAS provider quote fetch completed: fundCount={}, quoteCount={}, historyRecordCount={}",
                mappings.size(),
                quotes.size(),
                historyRecords.size()
        );
        return new ProviderFetchResult(List.copyOf(quotes), List.copyOf(historyRecords));
    }

    @Override
    public List<MarketQuote> fetchQuotes(ProviderFetchRequest request) {
        return fetch(request).quotes();
    }

    private List<InstrumentRegistryService.ResolvedMapping> resolveMappings(ProviderFetchRequest request) {
        List<InstrumentRegistryService.ResolvedMapping> mappings = instrumentRegistryService.resolveMappings(DataSource.TEFAS).mappings();
        if (request == null || !request.hasSymbolFilter()) {
            return mappings;
        }

        Set<String> requestedSymbols = request.symbols().stream()
                .map(symbolNormalizer::normalize)
                .flatMap(java.util.Optional::stream)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        return mappings.stream()
                .filter(mapping -> requestedSymbols.contains(mapping.symbol()))
                .toList();
    }

    private List<MarketHistoryRecord> fetchHistoryRecords(List<InstrumentRegistryService.ResolvedMapping> mappings,
                                                          LocalDate from,
                                                          LocalDate to) {
        LocalDate startDate = from == null ? LocalDate.now(clock).minusDays(89) : from;
        LocalDate endDate = to == null ? LocalDate.now(clock) : to;
        List<MarketHistoryRecord> historyRecords = new ArrayList<>();

        for (InstrumentRegistryService.ResolvedMapping mapping : mappings) {
            for (DateRange chunk : partition(startDate, endDate, 90)) {
                try {
                    List<TefasPoint> chunkPoints = fetchHistoryPoints(mapping.providerSymbol(), chunk.startDate(), chunk.endDate());
                    chunkPoints.stream()
                            .sorted(Comparator.comparing(TefasPoint::priceDate))
                            .map(point -> toHistoryRecord(mapping, point))
                            .forEach(historyRecords::add);
                } catch (Exception ex) {
                    log.warn(
                            "TEFAS history chunk failed: symbol={}, providerSymbol={}, chunkStart={}, chunkEnd={}, error={}",
                            mapping.symbol(),
                            mapping.providerSymbol(),
                            chunk.startDate(),
                            chunk.endDate(),
                            ex.getMessage()
                    );
                }
            }
        }

        return List.copyOf(historyRecords);
    }

    private TefasPoint fetchCurrentPoint(String providerSymbol) {
        URI uri = UriComponentsBuilder.fromHttpUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .path(normalizePath(properties.getCurrentQuotePath()))
                .queryParam("fonKodu", providerSymbol)
                .build(true)
                .toUri();
        return fetchPoints(uri, providerSymbol).stream()
                .max(Comparator.comparing(TefasPoint::priceDate))
                .orElse(null);
    }

    private TefasPoint fetchPreviousPoint(String providerSymbol, LocalDate latestDate) {
        if (latestDate == null) {
            return null;
        }
        LocalDate startDate = latestDate.minusDays(7);
        URI uri = UriComponentsBuilder.fromHttpUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .path(normalizePath(properties.getHistoryPath()))
                .queryParam("fonKodu", providerSymbol)
                .queryParam("baslangicTarih", startDate.format(TEFAS_DATE_FORMAT))
                .queryParam("bitisTarih", latestDate.format(TEFAS_DATE_FORMAT))
                .build(true)
                .toUri();

        List<TefasPoint> points = fetchPoints(uri, providerSymbol).stream()
                .filter(point -> point.priceDate().isBefore(latestDate))
                .sorted(Comparator.comparing(TefasPoint::priceDate))
                .toList();
        return points.isEmpty() ? null : points.getLast();
    }

    private List<TefasPoint> fetchHistoryPoints(String providerSymbol, LocalDate startDate, LocalDate endDate) {
        URI uri = UriComponentsBuilder.fromHttpUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .path(normalizePath(properties.getHistoryPath()))
                .queryParam("fonKodu", providerSymbol)
                .queryParam("baslangicTarih", startDate.format(TEFAS_DATE_FORMAT))
                .queryParam("bitisTarih", endDate.format(TEFAS_DATE_FORMAT))
                .build(true)
                .toUri();
        return fetchPoints(uri, providerSymbol);
    }

    private List<TefasPoint> fetchPoints(URI uri, String providerSymbol) {
        tokenBucket.acquire();

        RequestEntity<Void> request = RequestEntity.method(HttpMethod.GET, uri)
                .header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.USER_AGENT, "FinansPortal/1.0")
                .build();

        log.info("TEFAS request started: providerSymbol={}, uri={}", providerSymbol, uri);
        ResponseEntity<String> response = restTemplate.exchange(request, String.class);
        List<TefasPoint> points = parsePoints(response.getBody(), providerSymbol);
        log.info("TEFAS request completed: providerSymbol={}, pointCount={}", providerSymbol, points.size());
        return points;
    }

    private List<TefasPoint> parsePoints(String body, String providerSymbol) {
        if (body == null || body.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isArray()) {
                return List.of();
            }

            List<TefasPoint> points = new ArrayList<>();
            for (JsonNode item : dataNode) {
                BigDecimal price = decimal(item.get("FIYAT"));
                LocalDate priceDate = localDate(item.get("TARIH"));
                if (price == null || priceDate == null) {
                    continue;
                }
                points.add(new TefasPoint(providerSymbol, priceDate, price));
            }
            return List.copyOf(points);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse TEFAS response", ex);
        }
    }

    private MarketQuote toQuote(InstrumentRegistryService.ResolvedMapping mapping,
                                TefasPoint currentPoint,
                                TefasPoint previousPoint) {
        return new MarketQuote(
                mapping.symbol(),
                mapping.displayName(),
                mapping.instrumentType(),
                currentPoint.price(),
                calculateChangeRate(previousPoint, currentPoint),
                resolveCurrency(mapping),
                DataSource.TEFAS,
                currentPoint.priceDate().atStartOfDay().toInstant(ZoneOffset.UTC),
                clock.instant()
        );
    }

    private MarketHistoryRecord toHistoryRecord(InstrumentRegistryService.ResolvedMapping mapping, TefasPoint point) {
        return new MarketHistoryRecord(
                mapping.symbol(),
                mapping.displayName(),
                mapping.instrumentType(),
                DataSource.TEFAS,
                point.priceDate(),
                point.price(),
                resolveCurrency(mapping)
        );
    }

    private String resolveCurrency(InstrumentRegistryService.ResolvedMapping mapping) {
        return mapping.currency() == null || mapping.currency().isBlank() ? properties.getCurrency() : mapping.currency();
    }

    private BigDecimal calculateChangeRate(TefasPoint previousPoint, TefasPoint currentPoint) {
        if (previousPoint == null || previousPoint.price().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return currentPoint.price()
                .subtract(previousPoint.price())
                .multiply(BigDecimal.valueOf(100))
                .divide(previousPoint.price(), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String text = node.asText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", "."));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate localDate(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), TEFAS_DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private List<DateRange> partition(LocalDate startDate, LocalDate endDate, int chunkDays) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return List.of();
        }
        List<DateRange> ranges = new ArrayList<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            LocalDate chunkEnd = cursor.plusDays(chunkDays - 1L);
            if (chunkEnd.isAfter(endDate)) {
                chunkEnd = endDate;
            }
            ranges.add(new DateRange(cursor, chunkEnd));
            cursor = chunkEnd.plusDays(1);
        }
        return List.copyOf(ranges);
    }

    private boolean isHistoryRequest(ProviderFetchRequest request) {
        return request != null && (request.from() != null || request.to() != null);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("TEFAS base URL is not configured");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("TEFAS path is not configured");
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private record TefasPoint(String providerSymbol, LocalDate priceDate, BigDecimal price) {
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }

    static final class TokenBucket {
        private final int capacity;
        private final double refillPerSecond;
        private final Clock clock;
        private double tokens;
        private long lastRefillMillis;

        TokenBucket(int capacity, Clock clock) {
            this.capacity = capacity;
            this.refillPerSecond = capacity / 60.0d;
            this.clock = clock;
            this.tokens = capacity;
            this.lastRefillMillis = clock.millis();
        }

        synchronized void acquire() {
            refill();
            while (tokens < 1.0d) {
                long sleepMs = Math.max((long) Math.ceil((1.0d - tokens) / refillPerSecond * 1000.0d), 1L);
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("TEFAS token bucket wait interrupted", ex);
                }
                refill();
            }
            tokens -= 1.0d;
        }

        private void refill() {
            long now = clock.millis();
            long elapsedMillis = Math.max(now - lastRefillMillis, 0L);
            if (elapsedMillis == 0L) {
                return;
            }
            double refillAmount = (elapsedMillis / 1000.0d) * refillPerSecond;
            tokens = Math.min(capacity, tokens + refillAmount);
            lastRefillMillis = now;
        }
    }
}
