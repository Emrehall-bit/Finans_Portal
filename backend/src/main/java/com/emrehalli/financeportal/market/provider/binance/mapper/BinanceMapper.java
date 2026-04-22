package com.emrehalli.financeportal.market.provider.binance.mapper;

import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.enums.InstrumentType;
import com.emrehalli.financeportal.market.enums.MarketDataFreshness;
import com.emrehalli.financeportal.market.provider.binance.dto.BinanceTickerItem;
import com.emrehalli.financeportal.market.provider.binance.dto.BinanceTickerResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
public class BinanceMapper {

    private static final Logger logger = LogManager.getLogger(BinanceMapper.class);

    public List<MarketDataDto> mapSnapshot(BinanceTickerResponse response) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            logger.info("Binance mapper returned an empty list because snapshot response is empty.");
            return List.of();
        }

        List<MarketDataDto> mapped = response.getItems().stream()
                .map(item -> mapItem(item, response.getFetchedAt()))
                .filter(item -> item != null && item.getPrice() != null)
                .toList();

        logger.info("Binance mapper produced {} market records", mapped.size());
        return mapped;
    }

    public List<MarketDataDto> mapHistorical(BinanceTickerResponse response, String symbol) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            logger.info("Binance mapper returned an empty historical list for {} because response is empty or placeholder.", symbol);
            return List.of();
        }

        logger.info("Binance historical mapper is not implemented yet for symbol {}. Returning empty list.", symbol);
        return List.of();
    }

    private MarketDataDto mapItem(BinanceTickerItem item, LocalDateTime fetchedAt) {
        if (item == null) {
            return null;
        }

        String symbol = asString(item.getSymbol());
        BigDecimal price = asDecimal(item.getLastPrice());

        if (symbol == null || price == null) {
            logger.debug("Binance mapper skipped item because symbol or lastPrice is missing: {}", item);
            return null;
        }

        String currency = resolveQuoteCurrency(symbol);
        LocalDateTime priceTime = asEpochMillis(item.getCloseTime());

        return MarketDataDto.builder()
                .symbol(symbol)
                .name(buildDisplayName(symbol, currency))
                .instrumentType(InstrumentType.CRYPTO)
                .price(price)
                .changeAmount(asDecimal(item.getPriceChange()))
                .changePercent(asDecimal(item.getPriceChangePercent()))
                .currency(currency)
                .priceTime(priceTime)
                .fetchedAt(fetchedAt)
                .source("BINANCE")
                .freshness(MarketDataFreshness.from(priceTime, fetchedAt))
                .build();
    }

    private String buildDisplayName(String symbol, String currency) {
        if (currency != null && symbol.endsWith(currency)) {
            return symbol.substring(0, symbol.length() - currency.length()) + " / " + currency;
        }

        return symbol;
    }

    private String resolveQuoteCurrency(String symbol) {
        if (symbol == null) {
            return null;
        }

        List<String> knownQuotes = List.of("USDT", "BUSD", "FDUSD", "TRY", "BTC", "ETH");

        return knownQuotes.stream()
                .filter(symbol::endsWith)
                .findFirst()
                .orElse(null);
    }

    private String asString(String value) {
        return value == null ? null : value.trim();
    }

    private BigDecimal asDecimal(String value) {
        if (value == null) {
            return null;
        }

        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            logger.debug("Binance mapper could not parse decimal value {}", value);
            return null;
        }
    }

    private LocalDateTime asEpochMillis(Long value) {
        if (value == null) {
            return null;
        }

        try {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.systemDefault());
        } catch (Exception e) {
            logger.debug("Binance mapper could not parse epoch millis {}", value);
            return null;
        }
    }
}
