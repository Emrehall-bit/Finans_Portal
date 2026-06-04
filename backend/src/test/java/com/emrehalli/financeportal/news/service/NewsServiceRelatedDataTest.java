package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.admin.notification.service.NotificationService;
import com.emrehalli.financeportal.news.config.NewsNotificationProperties;
import com.emrehalli.financeportal.news.dto.response.NewsRelatedResponseDto;
import com.emrehalli.financeportal.news.entity.News;
import com.emrehalli.financeportal.news.repository.NewsProviderSyncStateRepository;
import com.emrehalli.financeportal.news.repository.NewsRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsServiceRelatedDataTest {

    @Test
    void relatedDataReturnsOnlyRelatedNews() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        NewsService service = buildService(newsRepository);

        News mainNews = baseNews(620L,
                "Fed faiz indirimi sinyali verdi, dolar ve altin fiyatlandi",
                "Fed aciklamasi sonrasinda altin ve dolar piyasalarinda yeni fiyatlama goruldu.",
                "INTEREST_BONDS");

        News strongCandidate = baseNews(621L,
                "Fed uyeleri faiz ve altin patikasini tartisiyor",
                "Altin fiyatlari ve tahvil piyasasi Fed mesajlari sonrasinda hareketlendi.",
                "INTEREST_BONDS");

        when(newsRepository.findById(620L)).thenReturn(Optional.of(mainNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of(strongCandidate));

        NewsRelatedResponseDto response = service.getRelatedData(620L);

        assertThat(response.relatedNews()).hasSize(1);
        assertThat(response.relatedNews().get(0).id()).isEqualTo(621L);
    }

    @Test
    void relatedNewsOnlyCategoryOverlapIsRejected() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        NewsService service = buildService(newsRepository);

        News mainNews = baseNews(610L,
                "TCMB faiz karari piyasalari hareketlendirdi, dolar yukseldi",
                "Merkez bankasi faiz kararinin ardindan dolar kuru ve altin fiyatlari reaksiyon verdi.",
                "INTEREST_BONDS");

        News weakCandidate = baseNews(611L,
                "Enflasyon beklentileri karisik seyretti, analistler tartisiyor",
                "Ekonomistler genel enflasyon gorunumunu degerlendirdi.",
                "INTEREST_BONDS");

        when(newsRepository.findById(610L)).thenReturn(Optional.of(mainNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of(weakCandidate));

        NewsRelatedResponseDto response = service.getRelatedData(610L);

        assertThat(response.relatedNews()).isEmpty();
    }

    private NewsService buildService(NewsRepository newsRepository) {
        return new NewsService(
                newsRepository,
                mock(com.emrehalli.financeportal.news.repository.NewsFavoriteRepository.class),
                mock(com.emrehalli.financeportal.user.repository.UserRepository.class),
                mock(com.emrehalli.financeportal.user.service.UserService.class),
                mock(NewsProviderSyncStateRepository.class),
                List.of(),
                mock(NewsImportanceScoringService.class),
                mock(NotificationService.class),
                mock(NewsNotificationProperties.class),
                new NewsPresentationMapper(),
                new NewsCategoryClassifier(),
                new FinancialImpactClassifier()
        );
    }

    private News baseNews(Long id, String title, String summary, String category) {
        return News.builder()
                .id(id)
                .externalId("NEWS-" + id)
                .title(title)
                .summary(summary)
                .source("Test Source")
                .provider("GUARDIAN")
                .language("en")
                .regionScope("GLOBAL")
                .category(category)
                .url("https://example.com/news/" + id)
                .publishedAt(LocalDateTime.of(2026, 5, 23, 9, 0))
                .importanceScore(80)
                .build();
    }
}
