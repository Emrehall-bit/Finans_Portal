package com.emrehalli.financeportal.market.provider.binance.mapper;

import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.binance.dto.BinanceKlineResponse;
import com.emrehalli.financeportal.market.provider.binance.dto.BinanceTickerResponse;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Component
public class BinanceMapper {

    public List<MarketQuote> toMarketQuotes(List<BinanceTickerResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return List.of();
        }

        Instant fetchedAt = Instant.now();

        return responses.stream()
                .flatMap(response -> toMarketQuote(response, fetchedAt).stream())
                .toList();
    }

    public List<MarketHistoryRecord> toHistoryRecords(String symbol, List<BinanceKlineResponse> responses) {
        if (isBlank(symbol) || responses == null || responses.isEmpty()) {
            return List.of();
        }

        String canonicalSymbol = symbol.trim();
        String currency = resolveCurrency(canonicalSymbol);
        String displayName = resolveDisplayName(canonicalSymbol, currency);

        return responses.stream()
                .flatMap(response -> toHistoryRecord(canonicalSymbol, displayName, currency, response).stream())
                .toList();
    }

    private Optional<MarketQuote> toMarketQuote(BinanceTickerResponse response, Instant fetchedAt) {
        if (response == null || isBlank(response.symbol())) {
            return Optional.empty();
        }

        Optional<BigDecimal> price = parseDecimal(response.lastPrice());
        if (price.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new MarketQuote(
                response.symbol().trim(),
                response.symbol().trim(),
                InstrumentType.CRYPTO,
                price.get(),
                parseDecimal(response.priceChangePercent()).orElse(null),
                "USDT",
                DataSource.BINANCE,
                toInstant(response.closeTime()),
                fetchedAt
        ));
    }

    private Optional<MarketHistoryRecord> toHistoryRecord(String symbol,
                                                          String displayName,
                                                          String currency,
                                                          BinanceKlineResponse response) {
        if (response == null || response.openTime() == null) {
            return Optional.empty();
        }

        Optional<BigDecimal> closePrice = parseDecimal(response.close());
        if (closePrice.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new MarketHistoryRecord(
                symbol,
                displayName,
                InstrumentType.CRYPTO,
                DataSource.BINANCE,
                LocalDate.ofInstant(Instant.ofEpochMilli(response.openTime()), ZoneOffset.UTC),
                closePrice.get(),
                currency
        ));
    }

    private Instant toInstant(Long closeTime) {
        return closeTime == null ? null : Instant.ofEpochMilli(closeTime);
    }

    private String resolveCurrency(String symbol) {
        if (symbol.endsWith("USDT")) {
            return "USDT";
        }

        return symbol;
    }

    private String resolveDisplayName(String symbol, String currency) {
        if ("USDT".equals(currency) && symbol.length() > currency.length()) {
            return symbol.substring(0, symbol.length() - currency.length()) + " / " + currency;
        }

        return symbol;
    }

    private Optional<BigDecimal> parseDecimal(String value) {
        if (isBlank(value)) {
            return Optional.empty();
        }

        try {
            return Optional.of(new BigDecimal(value.trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
