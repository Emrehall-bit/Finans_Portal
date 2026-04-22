package com.emrehalli.financeportal.market.provider.yahoo.mapper;

import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.enums.InstrumentType;
import com.emrehalli.financeportal.market.enums.MarketDataFreshness;
import com.emrehalli.financeportal.market.provider.yahoo.dto.YahooFinanceQuoteResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Component
public class YahooFinanceMapper {

    private static final Logger logger = LogManager.getLogger(YahooFinanceMapper.class);

    public List<MarketDataDto> mapSnapshot(YahooFinanceQuoteResponse response) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            logger.info("Yahoo Finance mapper returned an empty list because snapshot response is empty.");
            return List.of();
        }

        List<MarketDataDto> mapped = response.getItems().stream()
                .map(item -> mapItem(item, response.getFetchedAt()))
                .filter(item -> item != null && item.getPrice() != null)
                .toList();

        logger.info("Yahoo Finance mapper produced {} market records", mapped.size());
        return mapped;
    }

    public List<MarketDataDto> mapHistorical(YahooFinanceQuoteResponse response, String symbol) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            logger.info("Yahoo Finance mapper returned an empty historical list for {} because response is empty or placeholder.", symbol);
            return List.of();
        }

        logger.info("Yahoo Finance historical mapper is not implemented yet for symbol {}. Returning empty list.", symbol);
        return List.of();
    }

    private MarketDataDto mapItem(Map<String, Object> item, LocalDateTime fetchedAt) {
        if (item == null) {
            return null;
        }

        String symbol = asString(item.get("symbol"));
        BigDecimal price = asDecimal(item.get("regularMarketPrice"));

        if (symbol == null || price == null) {
            logger.debug("Yahoo mapper skipped item because symbol or regularMarketPrice is missing: {}", item);
            return null;
        }

        LocalDateTime priceTime = asEpochSeconds(item.get("regularMarketTime"));
        String name = firstNonBlank(
                asString(item.get("longName")),
                asString(item.get("shortName")),
                symbol
        );

        return MarketDataDto.builder()
                .symbol(symbol)
                .name(name)
                .instrumentType(resolveInstrumentType(symbol))
                .price(price)
                .changeAmount(asDecimal(item.get("regularMarketChange")))
                .changePercent(asDecimal(item.get("regularMarketChangePercent")))
                .currency(asString(item.get("currency")))
                .priceTime(priceTime)
                .fetchedAt(fetchedAt)
                .source("YAHOO")
                .freshness(MarketDataFreshness.from(priceTime, fetchedAt))
                .build();
    }

    private InstrumentType resolveInstrumentType(String symbol) {
        if (symbol != null && symbol.startsWith("^")) {
            return InstrumentType.INDEX;
        }

        return InstrumentType.EQUITY;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private BigDecimal asDecimal(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            logger.debug("Yahoo mapper could not parse decimal value {}", value);
            return null;
        }
    }

    private LocalDateTime asEpochSeconds(Object value) {
        if (value == null) {
            return null;
        }

        try {
            long epochSeconds = Long.parseLong(String.valueOf(value));
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
        } catch (NumberFormatException e) {
            logger.debug("Yahoo mapper could not parse epoch seconds {}", value);
            return null;
        }
    }
}
