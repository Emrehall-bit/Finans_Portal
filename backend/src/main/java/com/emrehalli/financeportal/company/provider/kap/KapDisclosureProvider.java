package com.emrehalli.financeportal.company.provider.kap;

import com.emrehalli.financeportal.company.enums.DisclosureType;
import com.emrehalli.financeportal.company.provider.kap.dto.KapDisclosureDto;
import com.emrehalli.financeportal.news.provider.kap.KapNewsClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Component
public class KapDisclosureProvider {

    private static final Logger logger = LogManager.getLogger(KapDisclosureProvider.class);
    private static final ZoneId KAP_ZONE = ZoneId.of("Europe/Istanbul");

    private final KapNewsClient kapNewsClient;

    public KapDisclosureProvider(KapNewsClient kapNewsClient) {
        this.kapNewsClient = kapNewsClient;
    }

    public List<KapDisclosureDto> fetchDisclosures(String searchQuery) {
        if (searchQuery == null || searchQuery.isBlank()) {
            return List.of();
        }

        logger.info("KAP disclosure fetch started. query={}", searchQuery);

        return kapNewsClient.fetchCompanyNews(searchQuery)
                .stream()
                .map(item -> KapDisclosureDto.builder()
                        .title(item.getTitle())
                        .kapUrl(item.getUrl())
                        .publishedAt(item.getPublishedAt() != null
                                ? item.getPublishedAt().atZone(KAP_ZONE).toOffsetDateTime()
                                : null)
                        .summary(item.getSummary())
                        .disclosureType(classifyDisclosure(item.getTitle()))
                        .build())
                .toList();
    }

    private DisclosureType classifyDisclosure(String title) {
        if (title == null) {
            return DisclosureType.GENERAL;
        }
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("finansal tablo") || lower.contains("bilanço")
                || lower.contains("gelir tablosu") || lower.contains("finansal rapor")
                || lower.contains("faaliyet raporu")) {
            return DisclosureType.FINANCIAL;
        }
        if (lower.contains("özel durum") || lower.contains("material event")) {
            return DisclosureType.SPECIAL;
        }
        return DisclosureType.GENERAL;
    }
}
