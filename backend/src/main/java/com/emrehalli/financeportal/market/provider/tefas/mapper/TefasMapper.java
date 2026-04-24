package com.emrehalli.financeportal.market.provider.tefas.mapper;

import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.tefas.dto.TefasFundResponse;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Component
public class TefasMapper {

    public List<MarketQuote> toMarketQuotes(List<TefasFundResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return List.of();
        }

        Instant fetchedAt = Instant.now();
        return responses.stream()
                .flatMap(response -> toMarketQuote(response, fetchedAt).stream())
                .toList();
    }

    public List<MarketHistoryRecord> toHistoryRecords(List<TefasFundResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return List.of();
        }

        return responses.stream()
                .flatMap(response -> toHistoryRecord(response).stream())
                .toList();
    }

    private Optional<MarketQuote> toMarketQuote(TefasFundResponse response, Instant fetchedAt) {
        if (response == null || isBlank(response.symbol())) {
            return Optional.empty();
        }

        Optional<BigDecimal> price = parseDecimal(response.price());
        if (price.isEmpty()) {
            return Optional.empty();
        }

        LocalDate priceDate = response.priceDate() == null ? LocalDate.now(ZoneOffset.UTC) : response.priceDate();

        return Optional.of(new MarketQuote(
                response.symbol().trim(),
                resolveDisplayName(response),
                InstrumentType.FUND,
                price.get(),
                parseDecimal(response.changeRate()).orElse(null),
                "TRY",
                DataSource.TEFAS,
                priceDate.atStartOfDay().toInstant(ZoneOffset.UTC),
                fetchedAt
        ));
    }

    private Optional<MarketHistoryRecord> toHistoryRecord(TefasFundResponse response) {
        if (response == null || isBlank(response.symbol())) {
            return Optional.empty();
        }

        Optional<BigDecimal> price = parseDecimal(response.price());
        if (price.isEmpty()) {
            return Optional.empty();
        }

        LocalDate priceDate = response.priceDate() == null ? LocalDate.now(ZoneOffset.UTC) : response.priceDate();

        return Optional.of(new MarketHistoryRecord(
                response.symbol().trim(),
                resolveDisplayName(response),
                InstrumentType.FUND,
                DataSource.TEFAS,
                priceDate,
                price.get(),
                "TRY"
        ));
    }

    private String resolveDisplayName(TefasFundResponse response) {
        return isBlank(response.displayName()) ? response.symbol().trim() : response.displayName().trim();
    }

    private Optional<BigDecimal> parseDecimal(String value) {
        if (isBlank(value)) {
            return Optional.empty();
        }

        String normalized = value.trim()
                .replace("%", "")
                .replace(".", "")
                .replace(",", ".");

        try {
            return Optional.of(new BigDecimal(normalized));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
