package com.emrehalli.financeportal.company.kap.client;

import com.emrehalli.financeportal.company.kap.dto.DisclosureFailedItemDto;
import com.emrehalli.financeportal.company.domain.enums.DisclosureType;
import com.emrehalli.financeportal.company.kap.dto.KapDisclosureDto;
import com.emrehalli.financeportal.company.kap.dto.KapSgbfDisclosureBasic;
import com.emrehalli.financeportal.company.kap.dto.KapSgbfItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class KapDisclosureProvider {

    private static final Logger logger = LogManager.getLogger(KapDisclosureProvider.class);
    private static final ZoneId KAP_ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter PUBLISH_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final String KAP_API_URL =
            "https://kap.org.tr/tr/api/company-detail/sgbf-data/{oid}/ALL/{days}";
    private static final String KAP_DISCLOSURE_URL_PREFIX = "https://www.kap.org.tr/tr/Bildirim/";

    private final RestClient restClient;

    public KapDisclosureProvider(RestClient restClient) {
        this.restClient = restClient;
    }

    public KapDisclosureProviderResult fetchDisclosures(String companyDetailOid) {
        return fetchDisclosures(companyDetailOid, 365);
    }

    public KapDisclosureProviderResult fetchDisclosures(String companyDetailOid, int days) {
        if (companyDetailOid == null || companyDetailOid.isBlank()) {
            return new KapDisclosureProviderResult(List.of(), List.of());
        }

        logger.info("KAP SGBF fetch started. companyDetailOid={}, days={}", companyDetailOid, days);

        KapSgbfItem[] rawItems;
        try {
            rawItems = restClient.get()
                    .uri(KAP_API_URL, companyDetailOid, days)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (compatible; FinancePortal/1.0)")
                    .retrieve()
                    .body(KapSgbfItem[].class);
        } catch (Exception e) {
            logger.error("KAP SGBF API call failed. companyDetailOid={}, days={}", companyDetailOid, days, e);
            throw e;
        }

        if (rawItems == null || rawItems.length == 0) {
            logger.info("KAP SGBF returned empty result. companyDetailOid={}, days={}", companyDetailOid, days);
            return new KapDisclosureProviderResult(List.of(), List.of());
        }

        List<KapDisclosureDto> disclosures = new ArrayList<>();
        List<DisclosureFailedItemDto> failedItems = new ArrayList<>();

        for (KapSgbfItem item : rawItems) {
            if (item.getDisclosureBasic() == null) {
                continue;
            }
            mapItem(item.getDisclosureBasic(), disclosures, failedItems);
        }

        logger.info("KAP SGBF parse complete. companyDetailOid={}, days={}, total={}, parsed={}, failed={}",
                companyDetailOid, days, rawItems.length, disclosures.size(), failedItems.size());

        return new KapDisclosureProviderResult(disclosures, failedItems);
    }

    private void mapItem(KapSgbfDisclosureBasic basic,
                         List<KapDisclosureDto> disclosures,
                         List<DisclosureFailedItemDto> failedItems) {
        String title = basic.getTitle();
        String disclosureIndex = basic.getDisclosureIndex();

        if (title == null || title.isBlank()) {
            failedItems.add(DisclosureFailedItemDto.builder()
                    .title(null)
                    .disclosureIndex(disclosureIndex)
                    .reason("title boş veya null")
                    .build());
            logger.debug("Disclosure skipped — blank title. disclosureIndex={}", disclosureIndex);
            return;
        }

        if (disclosureIndex == null || disclosureIndex.isBlank()) {
            failedItems.add(DisclosureFailedItemDto.builder()
                    .title(title)
                    .disclosureIndex(null)
                    .reason("disclosureIndex null")
                    .build());
            logger.debug("Disclosure skipped — null disclosureIndex. title={}", title);
            return;
        }

        OffsetDateTime publishedAt;
        try {
            publishedAt = LocalDateTime.parse(basic.getPublishDate(), PUBLISH_DATE_FORMATTER)
                    .atZone(KAP_ZONE)
                    .toOffsetDateTime();
        } catch (Exception e) {
            String reason = "publishDate parse edilemedi: " + basic.getPublishDate();
            failedItems.add(DisclosureFailedItemDto.builder()
                    .title(title)
                    .disclosureIndex(disclosureIndex)
                    .reason(reason)
                    .build());
            logger.warn("Disclosure skipped — {}. title={}", reason, title);
            return;
        }

        disclosures.add(KapDisclosureDto.builder()
                .title(title)
                .disclosureIndex(disclosureIndex)
                .kapUrl(KAP_DISCLOSURE_URL_PREFIX + disclosureIndex)
                .publishedAt(publishedAt)
                .summary(basic.getSummary())
                .disclosureType(mapDisclosureType(basic.getDisclosureType()))
                .kapYear(parseIntOrNull(basic.getYear()))
                .kapDonem(parseIntOrNull(basic.getDonem()))
                .kapPeriod(basic.getPeriod())
                .build());
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private DisclosureType mapDisclosureType(String rawType) {
        if (rawType == null) {
            return DisclosureType.GENERAL;
        }
        return switch (rawType) {
            case "FR"  -> DisclosureType.FINANCIAL;
            case "ODA" -> DisclosureType.SPECIAL;
            default    -> DisclosureType.GENERAL;
        };
    }
}



