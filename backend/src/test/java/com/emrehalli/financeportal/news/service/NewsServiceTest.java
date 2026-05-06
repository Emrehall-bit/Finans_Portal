package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.dto.response.NewsSyncResponseDto;
import com.emrehalli.financeportal.news.entity.News;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.provider.common.NewsProvider;
import com.emrehalli.financeportal.news.repository.NewsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

    @Mock
    private NewsRepository newsRepository;

    @Test
    void acceptsAaRssItemWithoutPublishedAtWhenRequiredFieldsExist() {
        when(newsRepository.findByExternalIdIn(Set.of("AA_RSS-1"))).thenReturn(Set.of());
        when(newsRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NewsProvider provider = new NewsProvider() {
            @Override
            public String getProviderName() {
                return "AA_RSS";
            }

            @Override
            public List<NewsItemDto> fetchLatestNews() {
                return List.of(NewsItemDto.builder()
                        .externalId("AA_RSS-1")
                        .title("Baslik mevcut ve kayit icin yeterli uzunlukta")
                        .source("Anadolu Ajansi")
                        .provider("AA_RSS")
                        .regionScope("TR")
                        .url("https://www.aa.com.tr/tr/ekonomi/ornek-haber-1")
                        .publishedAt(null)
                        .build());
            }

            @Override
            public List<NewsItemDto> fetchCompanyNews(String symbol) {
                return List.of();
            }
        };

        NewsService service = new NewsService(newsRepository, List.of(provider));

        NewsSyncResponseDto result = service.syncProvider(NewsProviderType.AA_RSS);

        ArgumentCaptor<List<News>> captor = ArgumentCaptor.forClass(List.class);
        verify(newsRepository).saveAll(captor.capture());

        assertThat(result.getValidCount()).isEqualTo(1);
        assertThat(result.getInvalidCount()).isZero();
        assertThat(result.getSavedCount()).isEqualTo(1);
        assertThat(captor.getValue()).singleElement().satisfies(news -> {
            assertThat(news.getTitle()).isEqualTo("Baslik mevcut ve kayit icin yeterli uzunlukta");
            assertThat(news.getPublishedAt()).isNull();
            assertThat(news.getImportanceScore()).isGreaterThan(0);
        });
    }

    @Test
    void recalculatesExistingImportanceScoreWhenExistingRecordHasZeroScore() {
        News existingNews = News.builder()
                .id(10L)
                .externalId("INVESTING_RSS-2")
                .title("TCMB faiz ve dolar haberi")
                .summary("Merkez bankasi ve piyasa etkisi")
                .source("Investing.com")
                .provider("INVESTING_RSS")
                .language("tr")
                .regionScope("GLOBAL")
                .url("https://www.investing.com/news/economy/ornek-haber-2")
                .importanceScore(0)
                .build();

        when(newsRepository.findByExternalIdIn(Set.of("INVESTING_RSS-2"))).thenReturn(Set.of(existingNews));
        when(newsRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NewsProvider provider = new NewsProvider() {
            @Override
            public String getProviderName() {
                return "INVESTING_RSS";
            }

            @Override
            public List<NewsItemDto> fetchLatestNews() {
                return List.of(NewsItemDto.builder()
                        .externalId("INVESTING_RSS-2")
                        .title("TCMB faiz ve dolar haberi")
                        .summary("Merkez bankasi ve piyasa etkisi")
                        .source("Investing.com")
                        .provider("INVESTING_RSS")
                        .language("tr")
                        .regionScope("GLOBAL")
                        .url("https://www.investing.com/news/economy/ornek-haber-2")
                        .build());
            }

            @Override
            public List<NewsItemDto> fetchCompanyNews(String symbol) {
                return List.of();
            }
        };

        NewsService service = new NewsService(newsRepository, List.of(provider));

        NewsSyncResponseDto result = service.syncProvider(NewsProviderType.INVESTING_RSS);

        assertThat(result.getExistingCount()).isEqualTo(1);
        verify(newsRepository).saveAll(argThat(iterable -> {
            java.util.Iterator<News> iterator = iterable.iterator();
            if (!iterator.hasNext()) {
                return false;
            }
            News updated = iterator.next();
            return !iterator.hasNext()
                    && updated.getImportanceScore() != null
                    && updated.getImportanceScore() > 0;
        }));
    }

    @Test
    void updatesExistingImageUrlWhenStoredRecordIsMissingImage() {
        News existingNews = News.builder()
                .id(15L)
                .externalId("AA_RSS-IMAGE-1")
                .title("Guncel ekonomi haberi")
                .summary("Ozet")
                .source("Anadolu Ajansi")
                .provider("AA_RSS")
                .language("tr")
                .regionScope("TR")
                .url("https://www.aa.com.tr/tr/ekonomi/ornek-haber")
                .imageUrl(null)
                .importanceScore(10)
                .build();

        when(newsRepository.findByExternalIdIn(Set.of("AA_RSS-IMAGE-1"))).thenReturn(Set.of(existingNews));
        when(newsRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NewsProvider provider = new NewsProvider() {
            @Override
            public String getProviderName() {
                return "AA_RSS";
            }

            @Override
            public List<NewsItemDto> fetchLatestNews() {
                return List.of(NewsItemDto.builder()
                        .externalId("AA_RSS-IMAGE-1")
                        .title("Guncel ekonomi haberi")
                        .summary("Ozet")
                        .source("Anadolu Ajansi")
                        .provider("AA_RSS")
                        .language("tr")
                        .regionScope("TR")
                        .url("https://www.aa.com.tr/tr/ekonomi/ornek-haber")
                        .imageUrl("https://cdn.example.com/aa-image.jpg")
                        .build());
            }

            @Override
            public List<NewsItemDto> fetchCompanyNews(String symbol) {
                return List.of();
            }
        };

        NewsService service = new NewsService(newsRepository, List.of(provider));

        NewsSyncResponseDto result = service.syncProvider(NewsProviderType.AA_RSS);

        assertThat(result.getExistingCount()).isEqualTo(1);
        verify(newsRepository).saveAll(argThat(iterable -> {
            java.util.Iterator<News> iterator = iterable.iterator();
            if (!iterator.hasNext()) {
                return false;
            }
            News updated = iterator.next();
            return !iterator.hasNext()
                    && "https://cdn.example.com/aa-image.jpg".equals(updated.getImageUrl());
        }));
    }

    @Test
    void clearsExistingLogoImageWhenProviderNoLongerSuppliesRealImage() {
        News existingNews = News.builder()
                .id(16L)
                .externalId("FINNHUB-LOGO-1")
                .title("Reuters sourced story")
                .summary("Summary")
                .source("Reuters")
                .provider("FINNHUB")
                .language("en")
                .regionScope("GLOBAL")
                .url("https://example.com/reuters-story")
                .imageUrl("https://static2.finnhub.io/file/finnhub/logo/reuters_logo.jpeg")
                .importanceScore(10)
                .build();

        when(newsRepository.findByExternalIdIn(Set.of("FINNHUB-LOGO-1"))).thenReturn(Set.of(existingNews));
        when(newsRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NewsProvider provider = new NewsProvider() {
            @Override
            public String getProviderName() {
                return "FINNHUB";
            }

            @Override
            public List<NewsItemDto> fetchLatestNews() {
                return List.of(NewsItemDto.builder()
                        .externalId("FINNHUB-LOGO-1")
                        .title("Reuters sourced story")
                        .summary("Summary")
                        .source("Reuters")
                        .provider("FINNHUB")
                        .language("en")
                        .regionScope("GLOBAL")
                        .url("https://example.com/reuters-story")
                        .imageUrl(null)
                        .build());
            }

            @Override
            public List<NewsItemDto> fetchCompanyNews(String symbol) {
                return List.of();
            }
        };

        NewsService service = new NewsService(newsRepository, List.of(provider));

        NewsSyncResponseDto result = service.syncProvider(NewsProviderType.FINNHUB);

        assertThat(result.getExistingCount()).isEqualTo(1);
        verify(newsRepository).saveAll(argThat(iterable -> {
            java.util.Iterator<News> iterator = iterable.iterator();
            if (!iterator.hasNext()) {
                return false;
            }
            News updated = iterator.next();
            return !iterator.hasNext() && updated.getImageUrl() == null;
        }));
    }
}
