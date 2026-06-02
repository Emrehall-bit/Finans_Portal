package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.news.dto.response.NewsResponseDto;
import com.emrehalli.financeportal.news.entity.News;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.enums.NewsQualityStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class NewsPresentationMapper {

    private static final int CONTENT_PREVIEW_LENGTH = 280;

    public NewsResponseDto toResponse(News news) {
        if (news == null) {
            return null;
        }

        boolean isKapDisclosure = resolveIsKapDisclosure(news);
        String qualityStatus = resolveQualityStatus(news, isKapDisclosure);
        String normalizedSummary = normalizeText(news.getSummary());

        return NewsResponseDto.builder()
                .id(news.getId())
                .externalId(news.getExternalId())
                .title(normalizeText(news.getTitle()))
                .summary(normalizedSummary)
                .contentPreview(resolveContentPreview(news, normalizedSummary))
                .source(news.getSource())
                .sourceName(resolveSourceName(news))
                .provider(news.getProvider())
                .language(normalizeText(news.getLanguage()))
                .regionScope(normalizeText(news.getRegionScope()))
                .category(normalizeText(news.getCategory()))
                .relatedSymbol(normalizeText(news.getRelatedSymbol()))
                .url(news.getUrl())
                .sourceUrl(news.getUrl())
                .imageUrl(normalizeImageUrl(news.getImageUrl(), isKapDisclosure))
                .publishedAt(news.getPublishedAt())
                .importanceScore(news.getImportanceScore())
                .qualityStatus(qualityStatus)
                .isKapDisclosure(isKapDisclosure)
                .disclosureType(resolveDisclosureType(news, isKapDisclosure))
                .build();
    }

    public boolean resolveIsKapDisclosure(News news) {
        if (news == null) {
            return false;
        }
        if (Boolean.TRUE.equals(news.getIsKapDisclosure())) {
            return true;
        }
        return isKapProvider(news.getProvider()) || "DISCLOSURE".equalsIgnoreCase(normalizeText(news.getCategory()));
    }

    public String resolveQualityStatus(News news, boolean isKapDisclosure) {
        if (isKapDisclosure) {
            return NewsQualityStatus.KAP_DISCLOSURE.name();
        }
        String stored = normalizeText(news.getQualityStatus());
        if (stored != null) {
            return stored;
        }
        String summary = normalizeText(news.getSummary());
        if (summary == null) {
            return NewsQualityStatus.SOURCE_LINK_ONLY.name();
        }
        if (isLikelyFullContent(news, summary)) {
            return NewsQualityStatus.FULL_CONTENT.name();
        }
        return NewsQualityStatus.SUMMARY_ONLY.name();
    }

    private String resolveSourceName(News news) {
        String provider = normalizeText(news.getProvider());
        if (provider != null) {
            String canonical = switch (provider.toUpperCase(Locale.ROOT)) {
                case "AA_RSS" -> "Anadolu Ajansı";
                case "CNBC_RSS" -> "CNBC";
                case "KAP" -> "KAP";
                default -> null;
            };
            if (canonical != null) {
                return canonical;
            }
        }
        String source = normalizeText(news.getSource());
        return source != null ? source : (provider != null ? provider : "Unknown");
    }

    private String resolveContentPreview(News news, String normalizedSummary) {
        if (normalizedSummary != null) {
            return truncate(cleanForPreview(normalizedSummary), CONTENT_PREVIEW_LENGTH);
        }

        String title = normalizeText(news.getTitle());
        return title == null ? "" : truncate(title, CONTENT_PREVIEW_LENGTH);
    }

    private String resolveDisclosureType(News news, boolean isKapDisclosure) {
        if (!isKapDisclosure) {
            return null;
        }
        String stored = normalizeText(news.getDisclosureType());
        return stored != null ? stored : "GENERAL";
    }

    private String normalizeImageUrl(String imageUrl, boolean isKapDisclosure) {
        if (isKapDisclosure) {
            return null;
        }
        return normalizeText(imageUrl);
    }

    private boolean isLikelyFullContent(News news, String summary) {
        if (summary == null) {
            return false;
        }
        if (summary.contains("\n\n") || summary.contains("## ")) {
            return true;
        }
        return isAaProvider(news.getProvider()) && summary.length() >= 500;
    }

    private boolean isKapProvider(String provider) {
        return NewsProviderType.KAP.name().equalsIgnoreCase(normalizeText(provider));
    }

    private boolean isAaProvider(String provider) {
        return NewsProviderType.AA_RSS.name().equalsIgnoreCase(normalizeText(provider));
    }

    private String cleanForPreview(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        return normalized
                .replace("## ", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)).trim() + "...";
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}




