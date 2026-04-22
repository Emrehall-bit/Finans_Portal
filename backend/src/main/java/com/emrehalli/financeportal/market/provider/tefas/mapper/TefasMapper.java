package com.emrehalli.financeportal.market.provider.tefas.mapper;

import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.enums.InstrumentType;
import com.emrehalli.financeportal.market.enums.MarketDataFreshness;
import com.emrehalli.financeportal.market.provider.tefas.dto.TefasFundResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class TefasMapper {

    private static final Logger logger = LogManager.getLogger(TefasMapper.class);

    public List<MarketDataDto> mapSnapshot(TefasFundResponse response) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            logger.info("TEFAS mapper returned an empty list because snapshot response is empty.");
            return List.of();
        }

        List<MarketDataDto> mapped = response.getItems().stream()
                .map(item -> mapItem(item, response.getFetchedAt()))
                .filter(item -> item != null && item.getPrice() != null)
                .toList();

        logger.info("TEFAS mapper produced {} market records", mapped.size());
        return mapped;
    }

    public List<MarketDataDto> mapHistorical(TefasFundResponse response, String symbol) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            logger.info("TEFAS mapper returned an empty historical list for {} because response is empty or placeholder.", symbol);
            return List.of();
        }

        logger.info("TEFAS historical mapper is not implemented yet for symbol {}. Returning empty list.", symbol);
        return List.of();
    }

    private MarketDataDto mapItem(Map<String, Object> item, java.time.LocalDateTime fetchedAt) {
        if (item == null) {
            return null;
        }

        String symbol = asString(item.get("symbol"));
        BigDecimal price = parseDecimal(item.get("price"));

        if (symbol == null || price == null) {
            logger.debug("TEFAS mapper skipped item because symbol or price is missing: {}", item);
            return null;
        }

        return MarketDataDto.builder()
                .symbol(symbol)
                .name(asString(item.get("name")))
                .instrumentType(InstrumentType.FUND)
                .price(price)
                .changePercent(parseDecimal(item.get("changePercent")))
                .currency("TRY")
                .priceTime(null)
                .fetchedAt(fetchedAt)
                .source("TEFAS")
                .freshness(MarketDataFreshness.from(null, fetchedAt))
                .build();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private BigDecimal parseDecimal(Object value) {
        if (value == null) {
            return null;
        }

        String normalized = String.valueOf(value)
                .replace("%", "")
                .replace(".", "")
                .replace(",", ".")
                .trim();

        if (normalized.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            logger.debug("TEFAS mapper could not parse decimal value {}", value);
            return null;
        }
    }
}
