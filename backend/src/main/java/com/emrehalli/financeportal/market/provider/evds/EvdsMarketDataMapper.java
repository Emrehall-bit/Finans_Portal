package com.emrehalli.financeportal.market.provider.evds;

import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.provider.evds.config.EvdsProperties;
import com.emrehalli.financeportal.market.provider.evds.dto.EvdsItem;
import com.emrehalli.financeportal.market.provider.evds.dto.EvdsResponse;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        List<ValidEvdsValue> validValues = extractValidValues(items, seriesConfig);
        if (validValues.isEmpty()) {
            return Optional.empty();
        }

        ValidEvdsValue latestValue = validValues.get(validValues.size() - 1);
        BigDecimal changeRate = validValues.size() > 1
                ? calculateChangeRate(validValues.get(validValues.size() - 2).price(), latestValue.price()).orElse(null)
                : null;

        return Optional.of(new MarketQuote(
                        seriesConfig.getSymbol(),
                        seriesConfig.getName(),
                        seriesConfig.getInstrumentType(),
                        latestValue.price(),
                        changeRate,
                        seriesConfig.getCurrency(),
                        DataSource.EVDS,
                        latestValue.priceTime(),
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

        for (String candidateKey : candidateKeys(seriesConfig)) {
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

    private List<String> candidateKeys(EvdsProperties.SeriesConfig seriesConfig) {
        return List.of(
                        seriesConfig.getEvdsKey(),
                        seriesConfig.getApiCode(),
                        normalizeEvdsKey(seriesConfig.getEvdsKey()),
                        normalizeEvdsKey(seriesConfig.getApiCode()),
                        denormalizeEvdsKey(seriesConfig.getEvdsKey()),
                        denormalizeEvdsKey(seriesConfig.getApiCode())
                ).stream()
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toList();
    }

    private String normalizeEvdsKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.replace('.', '_');
    }

    private String denormalizeEvdsKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.replace('_', '.');
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

    private Optional<BigDecimal> calculateChangeRate(BigDecimal previousPrice, BigDecimal latestPrice) {
        if (previousPrice == null || latestPrice == null || previousPrice.compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }

        return Optional.of(
                latestPrice.subtract(previousPrice)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(previousPrice, 4, RoundingMode.HALF_UP)
        );
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
