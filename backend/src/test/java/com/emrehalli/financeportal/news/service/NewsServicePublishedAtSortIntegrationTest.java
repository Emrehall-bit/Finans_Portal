package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.news.dto.request.NewsSearchRequest;
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
                LocalDateTime.of(2026, 4, 24, 9, 0)
        ), LocalDateTime.of(2026, 4, 24, 9, 5));
        persistWithCreatedAt(buildNews(
                "news-2",
                "Newest dated news",
                "Anadolu Ajansı",
                "AA_RSS",
                "TR",
                "economy",
                "https://example.com/2",
                LocalDateTime.of(2026, 4, 25, 12, 0)
        ), LocalDateTime.of(2026, 4, 25, 12, 5));
        persistWithCreatedAt(buildNews(
                "news-3",
                "Null dated but newer created",
                "Anadolu Ajansı",
                "AA_RSS",
                "TR",
                "economy",
                "https://example.com/3",
                null
        ), LocalDateTime.of(2026, 4, 25, 8, 0));
        persistWithCreatedAt(buildNews(
                "news-4",
                "Null dated second",
                "Bloomberg HT",
                "BLOOMBERG_HT",
                "LOCAL",
                "economy",
                "https://example.com/4",
                null
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
                "news-5",
                "English wire story",
                "Finnhub",
                "FINNHUB",
                "GLOBAL",
                "economy",
                "https://example.com/5",
                LocalDateTime.of(2026, 4, 25, 15, 0)
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

    private News buildNews(
            String externalId,
            String title,
            String source,
            String provider,
            String regionScope,
            String category,
            String url,
            LocalDateTime publishedAt
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
                .build();
    }

    private void persistWithCreatedAt(News news, LocalDateTime createdAt) {
        News saved = newsRepository.save(news);
        saved.setCreatedAt(createdAt);
        saved.setUpdatedAt(createdAt);
        newsRepository.save(saved);
    }
}
