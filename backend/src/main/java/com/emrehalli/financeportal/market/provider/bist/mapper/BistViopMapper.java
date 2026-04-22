package com.emrehalli.financeportal.market.provider.bist.mapper;

import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.enums.InstrumentType;
import com.emrehalli.financeportal.market.enums.MarketDataFreshness;
import com.emrehalli.financeportal.market.provider.bist.dto.BistViopResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BistViopMapper {

    private static final Logger logger = LogManager.getLogger(BistViopMapper.class);

    public List<MarketDataDto> mapSnapshot(BistViopResponse response) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            logger.info("BIST/VIOP mapper returned an empty list because response is empty.");
            return List.of();
        }

        List<MarketDataDto> mapped = response.getItems().stream()
                .map(item -> mapItem(item, response.getFetchedAt()))
                .toList();

        logger.info("BIST/VIOP mapper produced {} reference records", mapped.size());
        return mapped;
    }

    public List<MarketDataDto> mapHistorical(BistViopResponse response, String symbol) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            return List.of();
        }

        logger.info("BIST/VIOP historical mapper is not implemented yet for symbol {}. Returning empty list.", symbol);
        return List.of();
    }

    private MarketDataDto mapItem(Map<String, Object> item, java.time.LocalDateTime fetchedAt) {
        if (item == null) {
            return null;
        }

        return MarketDataDto.builder()
                .symbol(asString(item.get("symbol")))
                .name(asString(item.get("name")))
                .instrumentType(InstrumentType.FUTURE)
                .price(null)
                .currency(asString(item.get("currency")))
                .priceTime(null)
                .fetchedAt(fetchedAt)
                .source("BIST")
                .freshness(MarketDataFreshness.from(null, fetchedAt))
                .build();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
