package com.emrehalli.financeportal.market.provider.evds;

import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import com.emrehalli.financeportal.market.provider.evds.dto.EvdsItem;
import com.emrehalli.financeportal.market.provider.evds.dto.EvdsResponse;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class EvdsMarketDataMapper {

    private static final DateTimeFormatter EVDS_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public List<MarketQuote> toMarketQuotes(EvdsResponse response, List<EvdsProperties.SeriesConfig> seriesConfigs) {
        if (response == null || response.items().isEmpty() || seriesConfigs == null || seriesConfigs.isEmpty()) {
            return List.of();
        }

        Instant fetchedAt = Instant.now();

        return seriesConfigs.stream()
                .flatMap(seriesConfig -> toMarketQuote(response.items(), seriesConfig, fetchedAt).stream())
                .toList();
    }

    public List<MarketHistoryRecord> toHistoryRecords(EvdsResponse response, List<EvdsProperties.SeriesConfig> seriesConfigs) {
        if (response == null || response.items().isEmpty() || seriesConfigs == null || seriesConfigs.isEmpty()) {
            return List.of();
        }

        return seriesConfigs.stream()
                .flatMap(seriesConfig -> toHistoryRecords(response.items(), seriesConfig).stream())
                .toList();
    }

    private Optional<MarketQuote> toMarketQuote(List<EvdsItem> items,
                                                EvdsProperties.SeriesConfig seriesConfig,
                                                Instant fetchedAt) {
        return extractValidValues(items, seriesConfig).stream()
                .max(Comparator.comparing(ValidEvdsValue::priceDate))
                .map(value -> new MarketQuote(
                        seriesConfig.getSymbol(),
                        seriesConfig.getName(),
                        seriesConfig.getInstrumentType(),
                        value.price(),
                        null,
                        seriesConfig.getCurrency(),
                        DataSource.EVDS,
                        value.priceTime(),
                        fetchedAt
                ));
    }

    private List<MarketHistoryRecord> toHistoryRecords(List<EvdsItem> items, EvdsProperties.SeriesConfig seriesConfig) {
        return extractValidValues(items, seriesConfig).stream()
                .map(value -> new MarketHistoryRecord(
                        seriesConfig.getSymbol(),
                        seriesConfig.getName(),
                        seriesConfig.getInstrumentType(),
                        DataSource.EVDS,
                        value.priceDate(),
                        value.price(),
                        seriesConfig.getCurrency()
                ))
                .toList();
    }

    private List<ValidEvdsValue> extractValidValues(List<EvdsItem> items, EvdsProperties.SeriesConfig seriesConfig) {
        Map<LocalDate, ValidEvdsValue> valuesByDate = new LinkedHashMap<>();

        for (EvdsItem item : items) {
            toValidEvdsValue(item, seriesConfig)
                    .ifPresent(value -> valuesByDate.putIfAbsent(value.priceDate(), value));
        }

        return valuesByDate.values().stream()
                .sorted(Comparator.comparing(ValidEvdsValue::priceDate))
                .toList();
    }

    private Optional<ValidEvdsValue> toValidEvdsValue(EvdsItem item, EvdsProperties.SeriesConfig seriesConfig) {
        if (item == null) {
            return Optional.empty();
        }

        Optional<LocalDate> priceDate = parseDate(item.date());
        if (priceDate.isEmpty()) {
            return Optional.empty();
        }

        Optional<BigDecimal> price = parseDecimal(findValue(item.values(), seriesConfig));
        if (price.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new ValidEvdsValue(
                priceDate.get(),
                priceDate.get().atStartOfDay().toInstant(ZoneOffset.UTC),
                price.get()
        ));
    }

    private String findValue(Map<String, String> values, EvdsProperties.SeriesConfig seriesConfig) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        for (String candidateKey : List.of(seriesConfig.getEvdsKey(), seriesConfig.getApiCode())) {
            if (candidateKey == null || candidateKey.isBlank()) {
                continue;
            }

            String directValue = values.get(candidateKey);
            if (directValue != null) {
                return directValue;
            }

            Optional<String> matchedValue = values.entrySet().stream()
                    .filter(entry -> candidateKey.equalsIgnoreCase(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst();
            if (matchedValue.isPresent()) {
                return matchedValue.get();
            }
        }

        return null;
    }

    private Optional<BigDecimal> parseDecimal(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new BigDecimal(rawValue.trim().replace(",", ".")));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private Optional<LocalDate> parseDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(LocalDate.parse(rawDate.trim(), EVDS_DATE_FORMATTER));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private record ValidEvdsValue(
            LocalDate priceDate,
            Instant priceTime,
            BigDecimal price
    ) {
    }
}
