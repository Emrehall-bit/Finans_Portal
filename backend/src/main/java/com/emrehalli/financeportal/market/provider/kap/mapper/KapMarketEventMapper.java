package com.emrehalli.financeportal.market.provider.kap.mapper;

import com.emrehalli.financeportal.market.dto.event.MarketEventDto;
import com.emrehalli.financeportal.market.provider.kap.dto.KapDisclosureResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.List;
import java.util.Map;

@Component
public class KapMarketEventMapper {

    private static final Logger logger = LogManager.getLogger(KapMarketEventMapper.class);
    private static final DateTimeFormatter KAP_DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.forLanguageTag("tr-TR"));

    public List<MarketEventDto> map(KapDisclosureResponse response) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            logger.info("KAP mapper returned an empty list because response is empty.");
            return List.of();
        }

        List<MarketEventDto> mapped = response.getItems().stream()
                .map(item -> mapItem(item, response.getFetchedAt()))
                .filter(item -> item != null && item.getPublishedAt() != null)
                .toList();

        logger.info("KAP mapper produced {} market events", mapped.size());
        return mapped;
    }

    private MarketEventDto mapItem(Map<String, Object> item, LocalDateTime fetchedAt) {
        if (item == null) {
            return null;
        }

        String title = asString(item.get("title"));
        String summary = asString(item.get("summary"));
        LocalDateTime publishedAt = parseDateTime(asString(item.get("publishedAt")));

        if (title == null || publishedAt == null) {
            logger.debug("KAP mapper skipped disclosure because title or publishedAt is missing: {}", item);
            return null;
        }

        return MarketEventDto.builder()
                .source("KAP")
                .eventType(resolveEventType(title, summary, asString(item.get("disclosureType"))))
                .title(title)
                .symbol(asString(item.get("symbol")))
                .issuerCode(asString(item.get("symbol")))
                .publishedAt(publishedAt)
                .url(asString(item.get("url")))
                .summary(summary)
                .rawPayload(asString(item.get("rawPayload")))
                .fetchedAt(fetchedAt)
                .build();
    }

    private String resolveEventType(String title, String summary, String disclosureType) {
        String combined = ((title == null ? "" : title) + " " + (summary == null ? "" : summary)).toLowerCase(Locale.ROOT);

        if (combined.contains("halka arz")) {
            return "IPO";
        }

        if (combined.contains("sermaye art") || combined.contains("bedelli") || combined.contains("bedelsiz")) {
            return "CORPORATE_ACTION";
        }

        if (combined.contains("özel durum") || "ÖDA".equalsIgnoreCase(disclosureType)) {
            return "MATERIAL_DISCLOSURE";
        }

        return "DISCLOSURE";
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDateTime.parse(value.trim(), KAP_DATE_TIME);
        } catch (Exception e) {
            logger.debug("KAP mapper could not parse publishedAt value {}", value);
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
