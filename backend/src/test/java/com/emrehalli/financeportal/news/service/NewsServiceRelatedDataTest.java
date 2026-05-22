package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.admin.notification.service.NotificationService;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import com.emrehalli.financeportal.news.config.NewsNotificationProperties;
import com.emrehalli.financeportal.news.dto.response.NewsRelatedResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsResponseDto;
import com.emrehalli.financeportal.news.entity.News;
import com.emrehalli.financeportal.news.repository.NewsProviderSyncStateRepository;
import com.emrehalli.financeportal.news.repository.NewsRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsServiceRelatedDataTest {

    @Test
    void resolvesRelatedInstrumentsAndPrioritizedRelatedNews() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        NewsProviderSyncStateRepository syncStateRepository = mock(NewsProviderSyncStateRepository.class);
        NewsImportanceScoringService scoringService = mock(NewsImportanceScoringService.class);
        NotificationService notificationService = mock(NotificationService.class);
        NewsNotificationProperties notificationProperties = mock(NewsNotificationProperties.class);
        NewsPresentationMapper presentationMapper = mock(NewsPresentationMapper.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);

        NewsService service = new NewsService(
                newsRepository,
                syncStateRepository,
                List.of(),
                scoringService,
                notificationService,
                notificationProperties,
                presentationMapper,
                new NewsCategoryClassifier(),
                marketQueryService
        );

        News mainNews = News.builder()
                .id(100L)
                .externalId("AA-100")
                .title("THYAO ve Aselsan savunma ihracatinda one cikti")
                .summary("Turk Hava Yollari ve Aselsan hisseleri gundemde.")
                .source("Anadolu Ajansi")
                .provider("AA_RSS")
                .language("tr")
                .regionScope("TR")
                .category("ECONOMY")
                .relatedSymbol("THYAO")
                .url("https://example.com/main-news")
                .publishedAt(LocalDateTime.of(2026, 5, 19, 10, 0))
                .importanceScore(75)
                .build();

        News relatedNews = News.builder()
                .id(101L)
                .externalId("AA-101")
                .title("THYAO yeni rota planini acikladi")
                .summary("Turk Hava Yollari yeni hat acilisi yapacak.")
                .source("Anadolu Ajansi")
                .provider("AA_RSS")
                .language("tr")
                .regionScope("TR")
                .category("ECONOMY")
                .relatedSymbol("THYAO")
                .url("https://example.com/related-news")
                .publishedAt(LocalDateTime.of(2026, 5, 18, 9, 0))
                .importanceScore(68)
                .build();

        when(newsRepository.findById(100L)).thenReturn(Optional.of(mainNews));
        when(newsRepository.findRecentCandidatesForRelatedNews(eq(100L), eq("ECONOMY"), any(LocalDateTime.class)))
                .thenReturn(List.of(relatedNews));
        when(marketQueryService.findBySymbol("THYAO", InstrumentType.STOCK))
                .thenReturn(Optional.of(new MarketQueryService.MarketSnapshot(
                        "THYAO",
                        "Turk Hava Yollari",
                        BigDecimal.valueOf(294.50),
                        BigDecimal.valueOf(-1.83),
                        "BIST",
                        InstrumentType.STOCK.name(),
                        "TRY",
                        LocalDateTime.of(2026, 5, 19, 10, 5)
                )));
        when(marketQueryService.findBySymbol("ASELS", InstrumentType.STOCK)).thenReturn(Optional.empty());
        when(presentationMapper.toResponse(relatedNews)).thenReturn(NewsResponseDto.builder()
                .id(101L)
                .sourceName("Anadolu Ajansi")
                .build());

        NewsRelatedResponseDto response = service.getRelatedData(100L);

        assertThat(response.relatedInstruments()).extracting("symbol")
                .contains("THYAO", "ASELS");
        assertThat(response.relatedInstruments()).anyMatch(item ->
                "THYAO".equals(item.symbol())
                        && item.lastPrice() != null
                        && item.lastPrice().compareTo(BigDecimal.valueOf(294.5)) == 0
                        && "DIRECT".equals(item.relationType())
                        && "HIGH".equals(item.confidence()));
        assertThat(response.relatedNews()).hasSize(1);
        assertThat(response.relatedNews().get(0).id()).isEqualTo(101L);
        assertThat(response.relatedNews().get(0).sourceName()).isEqualTo("Anadolu Ajansi");
    }

    @Test
    void resolvesThemeBasedInstrumentsWhenNoDirectMatchExists() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        NewsProviderSyncStateRepository syncStateRepository = mock(NewsProviderSyncStateRepository.class);
        NewsImportanceScoringService scoringService = mock(NewsImportanceScoringService.class);
        NotificationService notificationService = mock(NotificationService.class);
        NewsNotificationProperties notificationProperties = mock(NewsNotificationProperties.class);
        NewsPresentationMapper presentationMapper = new NewsPresentationMapper();
        MarketQueryService marketQueryService = mock(MarketQueryService.class);

        NewsService service = new NewsService(
                newsRepository,
                syncStateRepository,
                List.of(),
                scoringService,
                notificationService,
                notificationProperties,
                presentationMapper,
                new NewsCategoryClassifier(),
                marketQueryService
        );

        News macroNews = News.builder()
                .id(200L)
                .externalId("AA-200")
                .title("TCMB faiz karari bankacilik sektorunu etkileyebilir")
                .summary("Faiz, kredi ve enflasyon beklentileri gundemde.")
                .source("Anadolu Ajansi")
                .provider("AA_RSS")
                .language("tr")
                .regionScope("TR")
                .category("ECONOMY")
                .url("https://example.com/macro-news")
                .publishedAt(LocalDateTime.of(2026, 5, 19, 10, 0))
                .importanceScore(80)
                .build();

        when(newsRepository.findById(200L)).thenReturn(Optional.of(macroNews));
        when(newsRepository.findRecentCandidatesForRelatedNews(eq(200L), eq("ECONOMY"), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(200L);

        assertThat(response.relatedInstruments()).extracting("symbol")
                .contains("AKBNK", "GARAN", "ISCTR", "YKBNK");
        assertThat(response.relatedInstruments()).allMatch(item -> "THEME".equals(item.relationType()) || "DIRECT".equals(item.relationType()));
        assertThat(response.relatedInstruments()).anyMatch(item ->
                "THEME".equals(item.relationType())
                        && "MEDIUM".equals(item.confidence())
                        && "Faiz ve kredi hassasiyeti".equals(item.reason()));
    }

    @Test
    void resolvesCarbonThemeWithLimitedAndDeduplicatedResults() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        NewsProviderSyncStateRepository syncStateRepository = mock(NewsProviderSyncStateRepository.class);
        NewsImportanceScoringService scoringService = mock(NewsImportanceScoringService.class);
        NotificationService notificationService = mock(NotificationService.class);
        NewsNotificationProperties notificationProperties = mock(NewsNotificationProperties.class);
        NewsPresentationMapper presentationMapper = new NewsPresentationMapper();
        MarketQueryService marketQueryService = mock(MarketQueryService.class);

        NewsService service = new NewsService(
                newsRepository,
                syncStateRepository,
                List.of(),
                scoringService,
                notificationService,
                notificationProperties,
                presentationMapper,
                new NewsCategoryClassifier(),
                marketQueryService
        );

        News climateNews = News.builder()
                .id(300L)
                .externalId("AA-300")
                .title("Karbon fiyatlandirmasi ve yesil donusum sanayi maliyetlerini artirabilir")
                .summary("Emisyon ve surdurulebilirlik baskisi agir sanayi ve enerji sirketlerini etkileyebilir.")
                .source("Anadolu Ajansi")
                .provider("AA_RSS")
                .language("tr")
                .regionScope("TR")
                .category("ECONOMY")
                .url("https://example.com/climate-news")
                .publishedAt(LocalDateTime.of(2026, 5, 19, 11, 0))
                .importanceScore(73)
                .build();

        when(newsRepository.findById(300L)).thenReturn(Optional.of(climateNews));
        when(newsRepository.findRecentCandidatesForRelatedNews(eq(300L), eq("ECONOMY"), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(300L);

        assertThat(response.relatedInstruments()).hasSizeLessThanOrEqualTo(6);
        assertThat(response.relatedInstruments()).extracting("symbol")
                .contains("EREGL", "KRDMD", "TUPRS");
        assertThat(response.relatedInstruments()).anyMatch(item ->
                "Karbon fiyatlandirmasi / emisyon maliyeti temasi".equals(item.reason())
                        && ("MEDIUM".equals(item.confidence()) || "LOW".equals(item.confidence())));
    }
}
