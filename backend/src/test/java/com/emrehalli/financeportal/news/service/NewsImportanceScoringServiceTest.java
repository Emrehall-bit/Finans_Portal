package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.news.entity.News;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class NewsImportanceScoringServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-08T12:00:00Z");

    private final NewsImportanceScoringService service =
            new NewsImportanceScoringService(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

    @Test
    void kap_source_gets_higher_score_than_generic_source() {
        News kapNews = neutralNews().provider("KAP").build();
        News genericNews = neutralNews().provider("UNKNOWN_RSS").build();

        assertThat(service.calculateScore(kapNews)).isEqualTo(14);
        assertThat(service.calculateScore(genericNews)).isEqualTo(0);
        assertThat(service.calculateScore(kapNews)).isGreaterThan(service.calculateScore(genericNews));
    }

    @Test
    void recent_news_gets_higher_score_than_old_news() {
        News recentNews = neutralNews()
                .publishedAt(FIXED_NOW.minusSeconds(5 * 60).atOffset(ZoneOffset.UTC).toLocalDateTime())
                .build();
        News oldNews = neutralNews()
                .publishedAt(FIXED_NOW.minus(java.time.Duration.ofDays(5)).atOffset(ZoneOffset.UTC).toLocalDateTime())
                .build();

        assertThat(service.calculateScore(recentNews)).isEqualTo(30);
        assertThat(service.calculateScore(oldNews)).isEqualTo(0);
        assertThat(service.calculateScore(recentNews)).isGreaterThan(service.calculateScore(oldNews));
    }

    @Test
    void high_impact_keywords_in_title_increase_score() {
        News withKeyword = neutralNews().title("Dolar haftaya sert yukselisle basladi").build();
        News withoutKeyword = neutralNews().title("Hava durumu bugun gunesli olacak").build();

        assertThat(service.calculateScore(withKeyword)).isEqualTo(18);
        assertThat(service.calculateScore(withoutKeyword)).isEqualTo(0);
        assertThat(service.calculateScore(withKeyword)).isGreaterThan(service.calculateScore(withoutKeyword));
    }

    @Test
    void turkish_region_or_language_increases_score() {
        News turkishRegion = neutralNews().regionScope("TR").language("en").build();
        News turkishLanguage = neutralNews().regionScope("US").language("tr").build();
        News neither = neutralNews().regionScope("US").language("en").build();

        assertThat(service.calculateScore(turkishRegion)).isEqualTo(5);
        assertThat(service.calculateScore(turkishLanguage)).isEqualTo(5);
        assertThat(service.calculateScore(neither)).isEqualTo(0);
    }

    @Test
    void news_with_summary_and_url_gets_content_quality_points() {
        News richContent = neutralNews()
                .summary("Kisa ozet metni okuyuculara aktarildi")
                .url("https://example.com/haber/123")
                .build();
        News poorContent = neutralNews().summary(null).url(null).build();

        assertThat(service.calculateScore(richContent)).isEqualTo(10);
        assertThat(service.calculateScore(poorContent)).isEqualTo(0);
        assertThat(service.calculateScore(richContent)).isGreaterThan(service.calculateScore(poorContent));
    }

    // Every field here contributes zero to the score, so each test can vary one dimension in isolation.
    private News.NewsBuilder neutralNews() {
        return News.builder()
                .id(1L)
                .title("Hava durumu bugun gunesli olacak")
                .summary(null)
                .provider(null)
                .language(null)
                .regionScope(null)
                .url(null)
                .publishedAt(null);
    }
}
