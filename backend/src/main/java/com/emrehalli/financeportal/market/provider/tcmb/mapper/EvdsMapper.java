package com.emrehalli.financeportal.market.provider.tcmb.mapper;

import com.emrehalli.financeportal.config.EvdsProperties;
import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.provider.tcmb.dto.EvdsResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EvdsMapper {

    private static final Logger logger = LogManager.getLogger(EvdsMapper.class);
    private static final DateTimeFormatter LEGACY_DAY_PATTERN = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter ISO_DAY_PATTERN = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter ISO_YEAR_MONTH_PATTERN = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter ISO_YEAR_SINGLE_DIGIT_MONTH_PATTERN = DateTimeFormatter.ofPattern("yyyy-M");
    private static final Set<String> NULL_FIELD_WARNINGS = ConcurrentHashMap.newKeySet();
    private static final Set<String> PRICE_TIME_WARNINGS = ConcurrentHashMap.newKeySet();

    private final EvdsProperties evdsProperties;

    public EvdsMapper(EvdsProperties evdsProperties) {
        this.evdsProperties = evdsProperties;
    }

    public List<MarketDataDto> map(EvdsResponse response) {
        List<MarketDataDto> result = new ArrayList<>();

        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            logger.warn("EVDS response or items are null/empty. Returning empty market data list.");
            return result;
        }

        Map<String, Object> lastValidItem = findLastValidItem(response.getItems());
        if (lastValidItem == null) {
            logger.warn("No valid data found for configured EVDS series. Returning empty list.");
            return result;
        }

        LocalDateTime fetchedAt = LocalDateTime.now();
        LocalDateTime priceTime = resolvePriceTime(lastValidItem);

        for (EvdsProperties.SeriesItem seriesItem : evdsProperties.getSeries()) {
            addMarketDataIfPresent(result, lastValidItem, seriesItem, priceTime, fetchedAt);
        }

        logger.debug("EVDS mapping completed successfully. Mapped {} items.", result.size());
        return result;
    }

    private Map<String, Object> findLastValidItem(List<Map<String, Object>> items) {
        for (int i = items.size() - 1; i >= 0; i--) {
            Map<String, Object> item = items.get(i);

            boolean hasValidData = evdsProperties.getSeries().stream()
                    .filter(seriesItem -> seriesItem.getEvdsKey() != null && !seriesItem.getEvdsKey().isBlank())
                    .anyMatch(seriesItem -> item.get(seriesItem.getEvdsKey()) != null);

            if (hasValidData) {
                return item;
            }
        }

        return null;
    }

    private void addMarketDataIfPresent(List<MarketDataDto> result,
                                        Map<String, Object> item,
                                        EvdsProperties.SeriesItem seriesItem,
                                        LocalDateTime priceTime,
                                        LocalDateTime fetchedAt) {
        if (seriesItem.getEvdsKey() == null || seriesItem.getEvdsKey().isBlank()) {
            return;
        }

        Object value = item.get(seriesItem.getEvdsKey());
        if (value == null) {
            logNullField(seriesItem);
            return;
        }

        result.add(MarketDataDto.builder()
                .symbol(seriesItem.getSymbol())
                .name(seriesItem.getName())
                .instrumentType(seriesItem.getInstrumentType())
                .price(parsePrice(value))
                .currency(seriesItem.getCurrency())
                .priceTime(priceTime)
                .fetchedAt(fetchedAt)
                .source("EVDS")
                .build());
    }

    private BigDecimal parsePrice(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }

        return new BigDecimal(String.valueOf(value));
    }

    private LocalDateTime resolvePriceTime(Map<String, Object> item) {
        Object rawDate = item.get("Tarih");
        if (rawDate == null) {
            rawDate = item.get("DATE");
        }
        if (rawDate == null) {
            return null;
        }

        String dateValue = String.valueOf(rawDate).trim();
        return parsePriceTime(dateValue);
    }

    private LocalDateTime parsePriceTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value, ISO_DAY_PATTERN).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            // Try the next supported EVDS date format.
        }

        try {
            return LocalDate.parse(value, LEGACY_DAY_PATTERN).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            // Try the next supported EVDS date format.
        }

        try {
            return YearMonth.parse(value, ISO_YEAR_MONTH_PATTERN).atDay(1).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            // Try the next supported EVDS date format.
        }

        try {
            return YearMonth.parse(value, ISO_YEAR_SINGLE_DIGIT_MONTH_PATTERN).atDay(1).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            // Fall through to controlled warning.
        }

        if (PRICE_TIME_WARNINGS.add(value)) {
            logger.warn("Unable to parse EVDS price time from value: {}", value);
        } else {
            logger.debug("EVDS price time still cannot be parsed for value: {}", value);
        }

        return null;
    }

    private void logNullField(EvdsProperties.SeriesItem seriesItem) {
        String warningKey = seriesItem.getEvdsKey() + "|" + seriesItem.getSymbol();

        if (NULL_FIELD_WARNINGS.add(warningKey)) {
            logger.warn("EVDS field {} returned null for symbol {}. Skipping this instrument.",
                    seriesItem.getEvdsKey(), seriesItem.getSymbol());
        } else {
            logger.debug("EVDS field {} is still null for symbol {}. Instrument remains skipped.",
                    seriesItem.getEvdsKey(), seriesItem.getSymbol());
        }
    }
}
