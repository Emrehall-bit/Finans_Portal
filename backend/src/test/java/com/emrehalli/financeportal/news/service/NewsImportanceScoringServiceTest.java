package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.news.entity.News;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class NewsImportanceScoringServiceTest {

    private final NewsImportanceScoringService service = new NewsImportanceScoringService(
            Clock.fixed(Instant.parse("2026-04-25T12:00:00Z"), ZoneId.of("UTC"))
    );

    @Test
    void tcmbAndFaizNewsGetsHighScore() {
        News news = News.builder()
                .title("TCMB faiz kararı sonrası dolar ve borsa hareketlendi")
                .summary("Merkez bankası adımı piyasa üzerinde etkili oldu.")
                .provider("BLOOMBERG_HT")
                .language("tr")
                .regionScope("TR")
                .url("https://example.com/news-1")
                .publishedAt(LocalDateTime.of(2026, 4, 25, 11, 30))
                .build();

        int score = service.calculateScore(news);

        assertThat(score).isGreaterThanOrEqualTo(100);
    }

    @Test
    void nullPublishedAtDoesNotGetRecencyScoreButStillGetsKeywordScore() {
        News news = News.builder()
                .title("Enflasyon ve kredi piyasası görünümü")
                .summary(null)
                .provider("FINNHUB")
                .language("en")
                .regionScope("GLOBAL")
                .url("https://example.com/news-2")
                .publishedAt(null)
                .build();

        int score = service.calculateScore(news);

        assertThat(score).isGreaterThan(40);
        assertThat(score).isLessThan(70);
    }

    @Test
    void summaryPresenceIncreasesQualityScore() {
        News withoutSummary = News.builder()
                .title("Bilanço dönemi başladı")
                .provider("AA_RSS")
                .regionScope("TR")
                .language("tr")
                .url("https://example.com/news-3")
                .publishedAt(LocalDateTime.of(2026, 4, 25, 9, 0))
                .build();
        News withSummary = News.builder()
                .title("Bilanço dönemi başladı")
                .summary("Şirket sonuçları yatırımcıların odağında.")
                .provider("AA_RSS")
                .regionScope("TR")
                .language("tr")
                .url("https://example.com/news-3")
                .publishedAt(LocalDateTime.of(2026, 4, 25, 9, 0))
                .build();

        assertThat(service.calculateScore(withSummary))
                .isEqualTo(service.calculateScore(withoutSummary) + 5);
    }
}
