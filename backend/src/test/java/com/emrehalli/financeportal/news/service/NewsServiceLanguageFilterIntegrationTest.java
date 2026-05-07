package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.news.dto.request.NewsSearchRequest;
import com.emrehalli.financeportal.news.dto.response.NewsResponseDto;
import com.emrehalli.financeportal.news.entity.News;
import com.emrehalli.financeportal.news.repository.NewsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NewsServiceLanguageFilterIntegrationTest {

    @Autowired
    private NewsRepository newsRepository;

    @BeforeEach
    void cleanUp() {
        newsRepository.deleteAll();
    }

    @Test
    void getNewsReturnsOnlyRequestedLanguage() {
        newsRepository.saveAll(List.of(
                News.builder()
                        .externalId("lang-tr-1")
                        .title("Turkce haber")
                        .summary("Turkce ozet")
                        .source("AA")
                        .provider("AA_RSS")
                        .language("tr")
                        .regionScope("TR")
                        .category("ECONOMY")
                        .url("https://example.com/tr")
                        .publishedAt(LocalDateTime.now().minusHours(1))
                        .importanceScore(20)
                        .build(),
                News.builder()
                        .externalId("lang-en-1")
                        .title("English news")
                        .summary("English summary")
                        .source("Finnhub")
                        .provider("FINNHUB")
                        .language("en")
                        .regionScope("GLOBAL")
                        .category("general")
                        .url("https://example.com/en")
                        .publishedAt(LocalDateTime.now().minusMinutes(30))
                        .importanceScore(15)
                        .build()
        ));

        NewsService service = new NewsService(newsRepository, List.of());

        Page<NewsResponseDto> turkishPage = service.getNews(
                NewsSearchRequest.builder().language("tr").build(),
                0,
                20,
                "publishedAt",
                "desc"
        );

        Page<NewsResponseDto> englishPage = service.getNews(
                NewsSearchRequest.builder().language("en").build(),
                0,
                20,
                "publishedAt",
                "desc"
        );

        assertThat(turkishPage.getContent()).singleElement().satisfies(item ->
                assertThat(item.getLanguage()).isEqualTo("tr")
        );

        assertThat(englishPage.getContent()).singleElement().satisfies(item ->
                assertThat(item.getLanguage()).isEqualTo("en")
        );
    }

    @Test
    void getNewsReturnsOnlyRequestedProvider() {
        newsRepository.saveAll(List.of(
                News.builder()
                        .externalId("provider-aa-1")
                        .title("Yerel ekonomi haberi")
                        .summary("AA feed item")
                        .source("AA")
                        .provider("AA_RSS")
                        .language("tr")
                        .regionScope("TR")
                        .category("ECONOMY")
                        .url("https://example.com/aa")
                        .publishedAt(LocalDateTime.now().minusHours(2))
                        .importanceScore(12)
                        .build(),
                News.builder()
                        .externalId("provider-investing-1")
                        .title("Global macro update")
                        .summary("Investing feed item")
                        .source("Investing.com")
                        .provider("INVESTING_RSS")
                        .language("en")
                        .regionScope("GLOBAL")
                        .category("ECONOMY")
                        .url("https://example.com/investing")
                        .publishedAt(LocalDateTime.now().minusHours(1))
                        .importanceScore(11)
                        .build()
        ));

        NewsService service = new NewsService(newsRepository, List.of());

        Page<NewsResponseDto> investingPage = service.getNews(
                NewsSearchRequest.builder().provider("INVESTING_RSS").build(),
                0,
                20,
                "publishedAt",
                "desc"
        );

        assertThat(investingPage.getContent()).singleElement().satisfies(item ->
                assertThat(item.getProvider()).isEqualTo("INVESTING_RSS")
        );
    }

    @Test
    void investingProviderReturnsOnlyEnglishItemsForLanguageFilter() {
        newsRepository.saveAll(List.of(
                News.builder()
                        .externalId("provider-investing-en-1")
                        .title("Investing English item")
                        .summary("English feed item")
                        .source("Investing.com")
                        .provider("INVESTING_RSS")
                        .language("en")
                        .regionScope("GLOBAL")
                        .category("ECONOMY")
                        .url("https://example.com/investing-en")
                        .publishedAt(LocalDateTime.now().minusMinutes(20))
                        .importanceScore(11)
                        .build(),
                News.builder()
                        .externalId("provider-aa-tr-1")
                        .title("AA Turkce item")
                        .summary("Turkce feed item")
                        .source("AA")
                        .provider("AA_RSS")
                        .language("tr")
                        .regionScope("TR")
                        .category("ECONOMY")
                        .url("https://example.com/aa-tr")
                        .publishedAt(LocalDateTime.now().minusMinutes(10))
                        .importanceScore(12)
                        .build()
        ));

        NewsService service = new NewsService(newsRepository, List.of());

        Page<NewsResponseDto> englishInvestingPage = service.getNews(
                NewsSearchRequest.builder()
                        .provider("INVESTING_RSS")
                        .language("en")
                        .build(),
                0,
                20,
                "publishedAt",
                "desc"
        );

        Page<NewsResponseDto> turkishInvestingPage = service.getNews(
                NewsSearchRequest.builder()
                        .provider("INVESTING_RSS")
                        .language("tr")
                        .build(),
                0,
                20,
                "publishedAt",
                "desc"
        );

        assertThat(englishInvestingPage.getContent()).singleElement().satisfies(item -> {
            assertThat(item.getProvider()).isEqualTo("INVESTING_RSS");
            assertThat(item.getLanguage()).isEqualTo("en");
        });
        assertThat(turkishInvestingPage.getContent()).isEmpty();
    }
}
