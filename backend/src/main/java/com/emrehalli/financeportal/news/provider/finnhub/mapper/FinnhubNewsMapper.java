package com.emrehalli.financeportal.news.provider.finnhub.mapper;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.enums.NewsQualityStatus;
import com.emrehalli.financeportal.news.enums.NewsRegionScope;
import com.emrehalli.financeportal.news.provider.finnhub.dto.FinnhubNewsResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class FinnhubNewsMapper {

    public NewsItemDto map(FinnhubNewsResponse source) {
        if (source == null) {
            return null;
        }

        return NewsItemDto.builder()
                .externalId(resolveExternalId(source))
                .title(source.getHeadline())
                .summary(source.getSummary())
                .source(source.getSource() == null || source.getSource().isBlank() ? "Finnhub" : source.getSource())
                .provider(NewsProviderType.FINNHUB.name())
                .language("en")
                .regionScope(NewsRegionScope.GLOBAL.name())
                .category(source.getCategory())
                .relatedSymbol(source.getRelated())
                .url(source.getUrl())
                .imageUrl(resolveImageUrl(source.getImage()))
                .publishedAt(mapDate(source.getDatetime()))
                .qualityStatus(resolveQualityStatus(source.getSummary()))
                .isKapDisclosure(false)
                .build();
    }

    private String resolveQualityStatus(String summary) {
        return (summary == null || summary.isBlank())
                ? NewsQualityStatus.SOURCE_LINK_ONLY.name()
                : NewsQualityStatus.SUMMARY_ONLY.name();
    }

    private String resolveImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        String normalized = imageUrl.trim().toLowerCase();
        if (normalized.contains("/logo/") || normalized.contains("_logo.") || normalized.endsWith("logo.jpeg")
                || normalized.endsWith("logo.jpg") || normalized.endsWith("logo.png") || normalized.endsWith("logo.webp")) {
            return null;
        }

        return imageUrl.trim();
    }

    private String resolveExternalId(FinnhubNewsResponse source) {
        if (source.getId() > 0) {
            return "FINNHUB-" + source.getId();
        }
        return "FINNHUB-" + source.getHeadline() + "-" + source.getDatetime();
    }

    private LocalDateTime mapDate(long epochSeconds) {
        if (epochSeconds <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }
}



