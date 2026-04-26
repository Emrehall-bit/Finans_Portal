package com.emrehalli.financeportal.market.provider.bist.mapper;

import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.bist.dto.BistQuoteResponse;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Component
public class BistMapper {

    public List<MarketQuote> toMarketQuotes(List<BistQuoteResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return List.of();
        }

        Instant fetchedAt = Instant.now();
        return responses.stream()
                .flatMap(response -> toMarketQuote(response, fetchedAt).stream())
                .toList();
    }

    public List<MarketHistoryRecord> toHistoryRecords(List<BistQuoteResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return List.of();
        }

        return responses.stream()
                .flatMap(response -> toHistoryRecord(response).stream())
                .toList();
    }

    private Optional<MarketQuote> toMarketQuote(BistQuoteResponse response, Instant fetchedAt) {
        if (response == null || isBlank(response.symbol())) {
            return Optional.empty();
        }

        if (response.regularMarketPrice() == null) {
            return Optional.empty();
        }

        Instant priceTime = resolvePriceTime(response).orElse(fetchedAt);
        String symbol = canonicalSymbol(response.symbol());
        return Optional.of(new MarketQuote(
                symbol,
                resolveDisplayName(response, symbol),
                InstrumentType.STOCK,
                response.regularMarketPrice(),
                response.regularMarketChangePercent(),
                "TRY",
                DataSource.BIST,
                priceTime,
                fetchedAt
        ));
    }

    private Optional<MarketHistoryRecord> toHistoryRecord(BistQuoteResponse response) {
        if (response == null || isBlank(response.symbol())) {
            return Optional.empty();
        }

        if (response.regularMarketPrice() == null) {
            return Optional.empty();
        }

        String symbol = canonicalSymbol(response.symbol());
        LocalDate priceDate = resolvePriceTime(response)
                .map(instant -> instant.atZone(ZoneOffset.UTC).toLocalDate())
                .orElse(LocalDate.now(ZoneOffset.UTC));

        return Optional.of(new MarketHistoryRecord(
                symbol,
                resolveDisplayName(response, symbol),
                InstrumentType.STOCK,
                DataSource.BIST,
                priceDate,
                response.regularMarketPrice(),
                "TRY"
        ));
    }

    private Optional<Instant> resolvePriceTime(BistQuoteResponse response) {
        if (response.regularMarketTime() != null && response.regularMarketTime() > 0L) {
            return Optional.of(Instant.ofEpochSecond(response.regularMarketTime()));
        }

        return Optional.empty();
    }

    private String resolveDisplayName(BistQuoteResponse response, String fallbackSymbol) {
        if (!isBlank(response.shortName())) {
            return response.shortName().trim();
        }
        if (!isBlank(response.longName())) {
            return response.longName().trim();
        }
        return fallbackSymbol;
    }

    private String canonicalSymbol(String rawSymbol) {
        String symbol = rawSymbol.trim();
        return symbol.endsWith(".IS")
                ? symbol.substring(0, symbol.length() - 3)
                : symbol;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
