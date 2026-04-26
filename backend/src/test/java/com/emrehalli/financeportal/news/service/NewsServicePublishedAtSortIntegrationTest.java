package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.news.dto.request.NewsSearchRequest;
import com.emrehalli.financeportal.news.dto.response.NewsImportanceRecalculationResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsResponseDto;
import com.emrehalli.financeportal.news.entity.News;
import com.emrehalli.financeportal.news.repository.NewsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Import(NewsServicePublishedAtSortIntegrationTest.Config.class)
class NewsServicePublishedAtSortIntegrationTest {

    @TestConfiguration
    static class Config {
        @Bean
        NewsService newsService(NewsRepository newsRepository) {
            return new NewsService(newsRepository, List.of());
        }
    }

    @Autowired
    private NewsRepository newsRepository;

    @Autowired
    private NewsService newsService;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        newsRepository.deleteAll();

        persistWithCreatedAt(buildNews(
                "news-1",
                "Older dated news",
                "Bloomberg HT",
                "BLOOMBERG_HT",
                "LOCAL",
                "economy",
                "https://example.com/1",
                LocalDateTime.of(2026, 4, 24, 9, 0),
                35
        ), LocalDateTime.of(2026, 4, 24, 9, 5));
        persistWithCreatedAt(buildNews(
                "news-2",
                "Newest dated news",
                "Anadolu Ajansı",
                "AA_RSS",
                "TR",
                "economy",
                "https://example.com/2",
                LocalDateTime.of(2026, 4, 25, 12, 0),
                80
        ), LocalDateTime.of(2026, 4, 25, 12, 5));
        persistWithCreatedAt(buildNews(
                "news-3",
                "Null dated but newer created",
                "Anadolu Ajansı",
                "AA_RSS",
                "TR",
                "economy",
                "https://example.com/3",
                null,
                20
        ), LocalDateTime.of(2026, 4, 25, 8, 0));
        persistWithCreatedAt(buildNews(
                "news-4",
                "Null dated second",
                "Bloomberg HT",
                "BLOOMBERG_HT",
                "LOCAL",
                "economy",
                "https://example.com/4",
                null,
                15
        ), LocalDateTime.of(2026, 4, 25, 9, 0));
    }

    @Test
    void sortsPublishedAtDescWithNullsLast() {
        Page<NewsResponseDto> page = newsService.getNews(
                NewsSearchRequest.builder().build(),
                0,
                10,
                "publishedAt",
                "desc"
        );

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent()).extracting(NewsResponseDto::getExternalId)
                .containsExactly("news-2", "news-1", "news-4", "news-3");
    }

    @Test
    void sortsPublishedAtAscWithNullsLast() {
        Page<NewsResponseDto> page = newsService.getNews(
                NewsSearchRequest.builder().build(),
                0,
                10,
                "publishedAt",
                "asc"
        );

        assertThat(page.getContent()).extracting(NewsResponseDto::getExternalId)
                .containsExactly("news-1", "news-2", "news-4", "news-3");
    }

    @Test
    void sortsByImportanceScoreDescWithPublishedAtAndCreatedAtFallback() {
        persistWithCreatedAt(buildNews(
                "news-5",
                "Equal score but older date",
                "Finnhub",
                "FINNHUB",
                "GLOBAL",
                "economy",
                "https://example.com/5",
                LocalDateTime.of(2026, 4, 24, 8, 0),
                80
        ), LocalDateTime.of(2026, 4, 25, 10, 0));

        Page<NewsResponseDto> page = newsService.getNews(
                NewsSearchRequest.builder().build(),
                0,
                10,
                "importanceScore",
                "desc"
        );

        assertThat(page.getContent()).extracting(NewsResponseDto::getExternalId)
                .containsExactly("news-2", "news-5", "news-1", "news-3", "news-4");
        assertThat(page.getContent().get(0).getImportanceScore()).isEqualTo(80);
    }

    @Test
    void importanceScoreOrderingDiffersFromPublishedAtOrdering() {
        persistWithCreatedAt(buildNews(
                "news-7",
                "TCMB faiz ve dolar haberi",
                "Bloomberg HT",
                "BLOOMBERG_HT",
                "TR",
                "economy",
                "https://example.com/7",
                LocalDateTime.of(2026, 4, 23, 8, 0),
                120
        ), LocalDateTime.of(2026, 4, 25, 7, 0));

        Page<NewsResponseDto> latestPage = newsService.getNews(
                NewsSearchRequest.builder().build(),
                0,
                10,
                "publishedAt",
                "desc"
        );
        Page<NewsResponseDto> importantPage = newsService.getNews(
                NewsSearchRequest.builder().build(),
                0,
                10,
                "importanceScore",
                "desc"
        );

        assertThat(latestPage.getContent().get(0).getExternalId()).isEqualTo("news-2");
        assertThat(importantPage.getContent().get(0).getExternalId()).isEqualTo("news-7");
    }

    @Test
    void keepsFilterAndPaginationWorkingWithPublishedAtSort() {
        Page<NewsResponseDto> page = newsService.getNews(
                NewsSearchRequest.builder()
                        .provider("AA_RSS")
                        .category("economy")
                        .build(),
                0,
                1,
                "publishedAt",
                "desc"
        );

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).singleElement().satisfies(item ->
                assertThat(item.getExternalId()).isEqualTo("news-2")
        );
    }

    @Test
    void filtersByLanguageAndKeepsPageIndex() {
        persistWithCreatedAt(buildNews(
                "news-6",
                "English wire story",
                "Finnhub",
                "FINNHUB",
                "GLOBAL",
                "economy",
                "https://example.com/6",
                LocalDateTime.of(2026, 4, 25, 15, 0),
                50
        ), LocalDateTime.of(2026, 4, 25, 15, 5));

        Page<NewsResponseDto> firstPage = newsService.getNews(
                NewsSearchRequest.builder()
                        .language("tr")
                        .build(),
                0,
                1,
                "publishedAt",
                "desc"
        );

        Page<NewsResponseDto> secondPage = newsService.getNews(
                NewsSearchRequest.builder()
                        .language("tr")
                        .build(),
                1,
                1,
                "publishedAt",
                "desc"
        );

        assertThat(firstPage.getTotalElements()).isEqualTo(4);
        assertThat(firstPage.getNumber()).isEqualTo(0);
        assertThat(secondPage.getTotalElements()).isEqualTo(4);
        assertThat(secondPage.getNumber()).isEqualTo(1);
        assertThat(secondPage.getContent()).singleElement().satisfies(item ->
                assertThat(item.getExternalId()).isEqualTo("news-1")
        );
    }

    @Test
    void recalculateImportanceScoresUpdatesExistingZeroScoreRecords() {
        persistWithCreatedAt(buildNews(
                "news-8",
                "TCMB faiz karari ve kredi piyasasi",
                "Bloomberg HT",
                "BLOOMBERG_HT",
                "TR",
                "economy",
                "https://example.com/8",
                LocalDateTime.of(2026, 4, 25, 11, 0),
                0
        ), LocalDateTime.of(2026, 4, 25, 11, 5));

        NewsImportanceRecalculationResponseDto response = newsService.recalculateImportanceScores();
        News recalculated = newsRepository.findByExternalId("news-8").orElseThrow();

        assertThat(response.getTotalProcessed()).isGreaterThanOrEqualTo(5);
        assertThat(response.getUpdatedCount()).isGreaterThanOrEqualTo(1);
        assertThat(recalculated.getImportanceScore()).isGreaterThan(0);
    }

    private News buildNews(
            String externalId,
            String title,
            String source,
            String provider,
            String regionScope,
            String category,
            String url,
            LocalDateTime publishedAt,
            Integer importanceScore
    ) {
        return News.builder()
                .externalId(externalId)
                .title(title)
                .source(source)
                .provider(provider)
                .language("FINNHUB".equals(provider) ? "en" : "tr")
                .regionScope(regionScope)
                .category(category)
                .url(url)
                .publishedAt(publishedAt)
                .importanceScore(importanceScore)
                .build();
    }

    private void persistWithCreatedAt(News news, LocalDateTime createdAt) {
        News saved = newsRepository.save(news);
        entityManager.createNativeQuery("update news set created_at = :createdAt, updated_at = :updatedAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("updatedAt", createdAt)
                .setParameter("id", saved.getId())
                .executeUpdate();
        entityManager.clear();
    }
}
