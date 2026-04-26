package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.news.entity.News;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class NewsImportanceScoringService {

    private static final List<String> HIGH_IMPACT_KEYWORDS = List.of(
            "faiz", "enflasyon", "tcmb", "merkez bankasi", "fed", "dolar", "altin",
            "borsa", "bist", "petrol", "vergi", "resesyon", "kriz"
    );
    private static final List<String> MEDIUM_IMPACT_KEYWORDS = List.of(
            "halka arz", "bilanco", "banka", "ihracat", "ithalat", "buyume",
            "issizlik", "kredi", "tahvil", "fon", "piyasa"
    );

    private final Clock clock;

    public NewsImportanceScoringService() {
        this(Clock.systemDefaultZone());
    }

    NewsImportanceScoringService(Clock clock) {
        this.clock = clock;
    }

    public int calculateScore(News news) {
        if (news == null) {
            return 0;
        }

        int score = 0;
        score += calculateRecencyScore(news.getPublishedAt());
        score += calculateProviderScore(news.getProvider());
        score += calculateKeywordScore(news.getTitle(), news.getSummary());
        score += calculateRegionLanguageScore(news.getRegionScope(), news.getLanguage());
        score += calculateQualityScore(news.getSummary(), news.getUrl());
        return score;
    }

    private int calculateRecencyScore(LocalDateTime publishedAt) {
        if (publishedAt == null) {
            return 0;
        }

        Duration duration = Duration.between(publishedAt, LocalDateTime.now(clock));
        if (duration.isNegative()) {
            return 30;
        }

        long minutes = duration.toMinutes();
        if (minutes < 60) {
            return Math.max(24, 30 - (int) (minutes / 10));
        }
        if (minutes < 360) {
            return Math.max(18, 25 - (int) ((minutes - 60) / 60));
        }
        if (minutes < 1440) {
            return Math.max(10, 20 - (int) ((minutes - 360) / 180));
        }
        if (duration.toDays() < 3) {
            return Math.max(2, 10 - (int) duration.toDays() * 4);
        }
        return 0;
    }

    private int calculateProviderScore(String provider) {
        if (provider == null) {
            return 0;
        }
        return switch (provider.trim().toUpperCase(Locale.ROOT)) {
            case "BLOOMBERG_HT", "AA_RSS" -> 12;
            case "FINNHUB" -> 8;
            default -> 0;
        };
    }

    private int calculateKeywordScore(String title, String summary) {
        String normalizedTitle = normalize(title);
        String normalizedSummary = normalize(summary);
        return scoreKeywords(normalizedTitle, normalizedSummary, HIGH_IMPACT_KEYWORDS, 18, 10)
                + scoreKeywords(normalizedTitle, normalizedSummary, MEDIUM_IMPACT_KEYWORDS, 9, 5);
    }

    private int calculateRegionLanguageScore(String regionScope, String language) {
        boolean isTurkishRegion = regionScope != null && "TR".equalsIgnoreCase(regionScope.trim());
        boolean isTurkishLanguage = language != null && "tr".equalsIgnoreCase(language.trim());
        return (isTurkishRegion || isTurkishLanguage) ? 5 : 0;
    }

    private int calculateQualityScore(String summary, String url) {
        int score = 0;
        if (hasText(summary)) {
            score += 5;
        }
        if (hasText(url)) {
            score += 5;
        }
        return score;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value
                .toLowerCase(Locale.ROOT)
                .replace('ı', 'i');
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized;
    }

    private int scoreKeywords(
            String normalizedTitle,
            String normalizedSummary,
            List<String> keywords,
            int titleWeight,
            int summaryWeight
    ) {
        int score = 0;
        for (String keyword : keywords) {
            String normalizedKeyword = normalize(keyword);
            if (normalizedTitle.contains(normalizedKeyword)) {
                score += titleWeight;
            }
            if (normalizedSummary.contains(normalizedKeyword)) {
                score += summaryWeight;
            }
        }
        return score;
    }
}
