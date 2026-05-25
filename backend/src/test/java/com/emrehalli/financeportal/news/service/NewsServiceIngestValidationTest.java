package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.admin.notification.service.NotificationService;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import com.emrehalli.financeportal.news.config.NewsNotificationProperties;
import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.entity.News;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.provider.common.NewsProvider;
import com.emrehalli.financeportal.news.repository.NewsProviderSyncStateRepository;
import com.emrehalli.financeportal.news.repository.NewsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsServiceIngestValidationTest {

    @Test
    void normalizesTrackingUrlsAndSkipsExistingCanonicalDuplicate() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        NewsProviderSyncStateRepository syncStateRepository = mock(NewsProviderSyncStateRepository.class);
        NewsService service = buildService(newsRepository, syncStateRepository, List.of(
                new StubProvider(NewsProviderType.AA_RSS.name(), List.of(
                        NewsItemDto.builder()
                                .externalId("AA-NEW-1")
                                .title("TCMB faiz karari piyasalari etkiledi")
                                .summary("TCMB faiz karari sonrasi piyasalarda fiyatlama degisti ve bankacilik hisseleri izlendi. "
                                        + "Karar doviz ve tahvil piyasalarina da yansidi.")
                                .source("Anadolu Ajansi")
                                .provider(NewsProviderType.AA_RSS.name())
                                .language("tr")
                                .regionScope("TR")
                                .category("ECONOMY")
                                .url("https://example.com/news/tcmb?utm_source=x&fbclid=y&id=42")
                                .publishedAt(LocalDateTime.of(2026, 5, 22, 11, 0))
                                .qualityStatus("SUMMARY_ONLY")
                                .isKapDisclosure(false)
                                .build()
                ))
        ));

        when(syncStateRepository.findById(anyString())).thenReturn(Optional.empty());
        when(newsRepository.findByExternalIdIn(anyCollection())).thenReturn(Set.of());
        when(newsRepository.findByUrlIn(anyCollection())).thenReturn(Set.of(
                News.builder()
                        .id(5L)
                        .externalId("AA-OLD-1")
                        .title("TCMB faiz karari piyasalari etkiledi")
                        .summary("Mevcut kayit")
                        .source("Anadolu Ajansi")
                        .provider(NewsProviderType.AA_RSS.name())
                        .language("tr")
                        .regionScope("TR")
                        .category("ECONOMY")
                        .url("https://example.com/news/tcmb?id=42")
                        .publishedAt(LocalDateTime.of(2026, 5, 22, 10, 0))
                        .build()
        ));
        when(newsRepository.findRecentPotentialDuplicates(anyCollection(), anyCollection(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        var response = service.syncProvider(NewsProviderType.AA_RSS);

        verify(newsRepository, never()).saveAll(anyList());
        assertThat(response.getSavedCount()).isZero();
        assertThat(response.getDuplicateCount()).isEqualTo(1);
    }

    @Test
    void acceptsSummaryOnlyRejectsSourceLinkOnlyAndAppliesPublishedAtFallback() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        NewsProviderSyncStateRepository syncStateRepository = mock(NewsProviderSyncStateRepository.class);
        NewsService service = buildService(newsRepository, syncStateRepository, List.of(
                new StubProvider(NewsProviderType.CNBC_RSS.name(), List.of(
                        NewsItemDto.builder()
                                .externalId("CNBC-1")
                                .title("Fed faiz beklentileri piyasayi destekledi")
                                .summary("Fed faiz beklentileri hisse, tahvil ve doviz piyasalarinda iyimser fiyatlamaya neden oldu. "
                                        + "Yatirimcilar yeni enflasyon verilerini izliyor.")
                                .source("CNBC")
                                .provider(NewsProviderType.CNBC_RSS.name())
                                .language("en")
                                .regionScope("GLOBAL")
                                .category("ECONOMY")
                                .url("https://www.cnbc.com/markets/story?utm_medium=social")
                                .publishedAt(null)
                                .contentEnrichedAt(LocalDateTime.of(2026, 5, 22, 12, 15))
                                .qualityStatus("SUMMARY_ONLY")
                                .isKapDisclosure(false)
                                .build(),
                        NewsItemDto.builder()
                                .externalId("CNBC-2")
                                .title("Lifestyle roundup")
                                .summary("This article covers travel plans, beaches, recipes and summer tips for holiday readers with no investing language at all.")
                                .source("CNBC")
                                .provider(NewsProviderType.CNBC_RSS.name())
                                .language("en")
                                .regionScope("GLOBAL")
                                .category("Lifestyle")
                                .url("https://www.cnbc.com/life/story")
                                .publishedAt(LocalDateTime.of(2026, 5, 22, 12, 20))
                                .qualityStatus("SUMMARY_ONLY")
                                .isKapDisclosure(false)
                                .build(),
                        NewsItemDto.builder()
                                .externalId("CNBC-3")
                                .title("Quick link only")
                                .summary("Markets moved.")
                                .source("CNBC")
                                .provider(NewsProviderType.CNBC_RSS.name())
                                .language("en")
                                .regionScope("GLOBAL")
                                .category("ECONOMY")
                                .url("https://www.cnbc.com/source-link-only")
                                .publishedAt(LocalDateTime.of(2026, 5, 22, 12, 25))
                                .qualityStatus("SOURCE_LINK_ONLY")
                                .isKapDisclosure(false)
                                .build()
                ))
        ));

        when(syncStateRepository.findById(anyString())).thenReturn(Optional.empty());
        when(newsRepository.findByExternalIdIn(anyCollection())).thenReturn(Set.of());
        when(newsRepository.findByUrlIn(anyCollection())).thenReturn(Set.of());
        when(newsRepository.findRecentPotentialDuplicates(anyCollection(), anyCollection(), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(newsRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.syncProvider(NewsProviderType.CNBC_RSS);
        ArgumentCaptor<List<News>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(newsRepository).saveAll(savedCaptor.capture());

        assertThat(response.getSavedCount()).isEqualTo(1);
        assertThat(savedCaptor.getValue()).hasSize(1);
        assertThat(savedCaptor.getValue().get(0).getUrl()).isEqualTo("https://cnbc.com/markets/story");
        assertThat(savedCaptor.getValue().get(0).getPublishedAt()).isEqualTo(LocalDateTime.of(2026, 5, 22, 12, 15));
    }

    @Test
    void usesTitleAndSourceFallbackForDuplicateDetection() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        NewsProviderSyncStateRepository syncStateRepository = mock(NewsProviderSyncStateRepository.class);
        NewsService service = buildService(newsRepository, syncStateRepository, List.of(
                new StubProvider(NewsProviderType.AA_RSS.name(), List.of(
                        NewsItemDto.builder()
                                .externalId("AA-UNIQUE")
                                .title("Borsa gunu dususle tamamladi")
                                .summary("Borsa gunu dususle tamamladi ve bankacilik, tahvil ve doviz piyasalarinda hareketlilik izlendi.")
                                .source("Anadolu Ajansi")
                                .provider(NewsProviderType.AA_RSS.name())
                                .language("tr")
                                .regionScope("TR")
                                .category("ECONOMY")
                                .url("https://example.com/unique-path")
                                .publishedAt(LocalDateTime.of(2026, 5, 22, 18, 0))
                                .qualityStatus("SUMMARY_ONLY")
                                .isKapDisclosure(false)
                                .build()
                ))
        ));

        when(syncStateRepository.findById(anyString())).thenReturn(Optional.empty());
        when(newsRepository.findByExternalIdIn(anyCollection())).thenReturn(Set.of());
        when(newsRepository.findByUrlIn(anyCollection())).thenReturn(Set.of());
        when(newsRepository.findRecentPotentialDuplicates(anyCollection(), anyCollection(), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        News.builder()
                                .id(22L)
                                .externalId("AA-OLD-22")
                                .title("Borsa gunu dususle tamamladi")
                                .summary("Eski kayit")
                                .source("Anadolu Ajansi")
                                .provider(NewsProviderType.AA_RSS.name())
                                .language("tr")
                                .regionScope("TR")
                                .category("ECONOMY")
                                .url("https://example.com/another-path")
                                .publishedAt(LocalDateTime.of(2026, 5, 22, 17, 30))
                                .build()
                ));

        var response = service.syncProvider(NewsProviderType.AA_RSS);

        verify(newsRepository, never()).saveAll(anyList());
        assertThat(response.getDuplicateCount()).isEqualTo(1);
        assertThat(response.getSavedCount()).isZero();
    }

    @Test
    void normalizesNonKapCategoriesAndRejectsPurePolitics() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        NewsProviderSyncStateRepository syncStateRepository = mock(NewsProviderSyncStateRepository.class);
        NewsService service = buildService(newsRepository, syncStateRepository, List.of(
                new StubProvider(NewsProviderType.AA_RSS.name(), List.of(
                        NewsItemDto.builder()
                                .externalId("AA-CAT-1")
                                .title("TCMB faiz kararini acikladi")
                                .summary("Merkez bankasi faiz karari tahvil ve doviz piyasalarinda yakindan izlendi. "
                                        + "Bankacilik hisseleri, getiri egirisi ve para politikasi beklentileri yeniden fiyatlandi.")
                                .source("Anadolu Ajansi")
                                .provider(NewsProviderType.AA_RSS.name())
                                .language("tr")
                                .regionScope("TR")
                                .category("business")
                                .url("https://example.com/tcmb-karar")
                                .publishedAt(LocalDateTime.of(2026, 5, 22, 19, 0))
                                .qualityStatus("SUMMARY_ONLY")
                                .isKapDisclosure(false)
                                .build(),
                        NewsItemDto.builder()
                                .externalId("AA-CAT-2")
                                .title("Siyasi parti aciklama yapti")
                                .summary("Parti sozcusu secim kampanyasi ve meclis gundemine dair aciklama yapti.")
                                .source("Anadolu Ajansi")
                                .provider(NewsProviderType.AA_RSS.name())
                                .language("tr")
                                .regionScope("TR")
                                .category("general")
                                .url("https://example.com/politics")
                                .publishedAt(LocalDateTime.of(2026, 5, 22, 19, 5))
                                .qualityStatus("SUMMARY_ONLY")
                                .isKapDisclosure(false)
                                .build()
                ))
        ));

        when(syncStateRepository.findById(anyString())).thenReturn(Optional.empty());
        when(newsRepository.findByExternalIdIn(anyCollection())).thenReturn(Set.of());
        when(newsRepository.findByUrlIn(anyCollection())).thenReturn(Set.of());
        when(newsRepository.findRecentPotentialDuplicates(anyCollection(), anyCollection(), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(newsRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.syncProvider(NewsProviderType.AA_RSS);
        ArgumentCaptor<List<News>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(newsRepository).saveAll(savedCaptor.capture());

        assertThat(response.getSavedCount()).isEqualTo(1);
        assertThat(response.getInvalidCount()).isEqualTo(1);
        assertThat(savedCaptor.getValue()).singleElement()
                .extracting(News::getCategory)
                .isEqualTo(NewsCategoryClassifier.INTEREST_BONDS);
        assertThat(savedCaptor.getValue().get(0).getFilterTags())
                .contains(NewsCategoryClassifier.INTEREST_BONDS)
                .contains(NewsCategoryClassifier.FX)
                .contains(NewsCategoryClassifier.BANKING)
                .contains(NewsCategoryClassifier.STOCKS)
                .contains(NewsCategoryClassifier.GENERAL_ECONOMY)
                .doesNotContain(NewsCategoryClassifier.GLOBAL_MARKETS);
    }

    @Test
    void backfillFilterTagsDryRunDoesNotPersistChanges() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        NewsProviderSyncStateRepository syncStateRepository = mock(NewsProviderSyncStateRepository.class);
        NewsService service = buildService(newsRepository, syncStateRepository, List.of());

        News staleNews = News.builder()
                .id(101L)
                .externalId("AA-BACKFILL-1")
                .title("TCMB faiz karari sonrasi kur hareketlendi")
                .summary("Merkez bankasi karari sonrasi dolar/TL ve tahvil piyasasinda yeni fiyatlama olustu.")
                .source("Anadolu Ajansi")
                .provider(NewsProviderType.AA_RSS.name())
                .language("tr")
                .regionScope("TR")
                .category("business")
                .filterTags("GLOBAL_MARKETS,GOLD_COMMODITY")
                .url("https://example.com/backfill-1")
                .publishedAt(LocalDateTime.of(2026, 5, 22, 20, 0))
                .isKapDisclosure(false)
                .build();

        when(newsRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(staleNews)));

        var response = service.backfillFilterTags(500, true);

        verify(newsRepository, never()).saveAll(anyList());
        assertThat(response.getProcessedCount()).isEqualTo(1);
        assertThat(response.getUpdatedCount()).isEqualTo(1);
        assertThat(response.getSampleChanges()).hasSize(1);
        assertThat(staleNews.getCategory()).isEqualTo("business");
        assertThat(staleNews.getFilterTags()).isEqualTo("GLOBAL_MARKETS,GOLD_COMMODITY");
    }

    @Test
    void backfillFilterTagsUpdatesChangedNewsWhenDryRunDisabled() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        NewsProviderSyncStateRepository syncStateRepository = mock(NewsProviderSyncStateRepository.class);
        NewsService service = buildService(newsRepository, syncStateRepository, List.of());

        News staleNews = News.builder()
                .id(102L)
                .externalId("AA-BACKFILL-2")
                .title("TCMB faiz karari sonrasi kur hareketlendi")
                .summary("Merkez bankasi karari sonrasi dolar/TL ve tahvil piyasasinda yeni fiyatlama olustu.")
                .source("Anadolu Ajansi")
                .provider(NewsProviderType.AA_RSS.name())
                .language("tr")
                .regionScope("TR")
                .category("business")
                .filterTags("GLOBAL_MARKETS,GOLD_COMMODITY")
                .url("https://example.com/backfill-2")
                .publishedAt(LocalDateTime.of(2026, 5, 22, 20, 0))
                .isKapDisclosure(false)
                .build();

        when(newsRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(staleNews)));
        when(newsRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.backfillFilterTags(500, false);
        ArgumentCaptor<List<News>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(newsRepository).saveAll(savedCaptor.capture());

        assertThat(response.getProcessedCount()).isEqualTo(1);
        assertThat(response.getUpdatedCount()).isEqualTo(1);
        assertThat(savedCaptor.getValue()).singleElement()
                .extracting(News::getCategory)
                .isEqualTo(NewsCategoryClassifier.INTEREST_BONDS);
        assertThat(savedCaptor.getValue().get(0).getFilterTags())
                .contains(NewsCategoryClassifier.INTEREST_BONDS)
                .contains(NewsCategoryClassifier.FX)
                .doesNotContain(NewsCategoryClassifier.GOLD_COMMODITY);
    }

    @Test
    void backfillFilterTagsSkipsKapNews() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        NewsProviderSyncStateRepository syncStateRepository = mock(NewsProviderSyncStateRepository.class);
        NewsService service = buildService(newsRepository, syncStateRepository, List.of());

        News kapNews = News.builder()
                .id(103L)
                .externalId("KAP-BACKFILL-1")
                .title("KAP bildirimi")
                .summary("Sirket ozel durum aciklamasi yapti.")
                .source("KAP")
                .provider(NewsProviderType.KAP.name())
                .language("tr")
                .regionScope("TR")
                .category("DISCLOSURE")
                .filterTags("COMPANY")
                .url("https://kap.org.tr/tr/Bildirim/1")
                .publishedAt(LocalDateTime.of(2026, 5, 22, 20, 0))
                .isKapDisclosure(true)
                .build();

        when(newsRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(kapNews)));

        var response = service.backfillFilterTags(500, false);

        verify(newsRepository, never()).saveAll(anyList());
        assertThat(response.getProcessedCount()).isEqualTo(1);
        assertThat(response.getSkippedKapCount()).isEqualTo(1);
        assertThat(response.getUpdatedCount()).isZero();
        assertThat(kapNews.getCategory()).isEqualTo("DISCLOSURE");
        assertThat(kapNews.getFilterTags()).isEqualTo("COMPANY");
    }

    @Test
    void guardianItemPreservesImageAndFlowsThroughClassificationPipeline() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        NewsProviderSyncStateRepository syncStateRepository = mock(NewsProviderSyncStateRepository.class);
        NewsService service = buildService(newsRepository, syncStateRepository, List.of(
                new StubProvider(NewsProviderType.GUARDIAN.name(), List.of(
                        NewsItemDto.builder()
                                .externalId("GUARDIAN:fed-1")
                                .title("Fed signals slower rate path as dollar and bond markets react")
                                .summary("The Fed signaled a slower rate path as bond yields, the dollar and global markets repriced the outlook.")
                                .source("The Guardian")
                                .provider(NewsProviderType.GUARDIAN.name())
                                .language("en")
                                .regionScope("GLOBAL")
                                .category("ECONOMY")
                                .url("https://www.theguardian.com/business/2026/may/22/fed-signals-slower-rate-path")
                                .imageUrl("https://media.guim.co.uk/fed.jpg")
                                .publishedAt(LocalDateTime.of(2026, 5, 22, 21, 0))
                                .qualityStatus("FULL_CONTENT")
                                .isKapDisclosure(false)
                                .build()
                ))
        ));

        when(syncStateRepository.findById(anyString())).thenReturn(Optional.empty());
        when(newsRepository.findByExternalIdIn(anyCollection())).thenReturn(Set.of());
        when(newsRepository.findByUrlIn(anyCollection())).thenReturn(Set.of());
        when(newsRepository.findRecentPotentialDuplicates(anyCollection(), anyCollection(), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(newsRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.syncProvider(NewsProviderType.GUARDIAN);
        ArgumentCaptor<List<News>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(newsRepository).saveAll(savedCaptor.capture());

        assertThat(response.getSavedCount()).isEqualTo(1);
        assertThat(savedCaptor.getValue()).singleElement()
                .satisfies(news -> {
                    assertThat(news.getImageUrl()).isEqualTo("https://media.guim.co.uk/fed.jpg");
                    assertThat(news.getQualityStatus()).isEqualTo("FULL_CONTENT");
                    assertThat(news.getCategory()).isEqualTo(NewsCategoryClassifier.INTEREST_BONDS);
                    assertThat(news.getFilterTags())
                            .contains(NewsCategoryClassifier.INTEREST_BONDS)
                            .contains(NewsCategoryClassifier.GLOBAL_MARKETS);
                });
    }

    @Test
    void guardianDuplicateDetectionSkipsExistingCanonicalUrl() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        NewsProviderSyncStateRepository syncStateRepository = mock(NewsProviderSyncStateRepository.class);
        NewsService service = buildService(newsRepository, syncStateRepository, List.of(
                new StubProvider(NewsProviderType.GUARDIAN.name(), List.of(
                        NewsItemDto.builder()
                                .externalId("GUARDIAN:new-1")
                                .title("Dollar and bond markets react to Fed comments")
                                .summary("Markets repriced after Fed comments, investors tracked the dollar, bond yields reset expectations and global traders reassessed rate pricing through the session.")
                                .source("The Guardian")
                                .provider(NewsProviderType.GUARDIAN.name())
                                .language("en")
                                .regionScope("GLOBAL")
                                .category("ECONOMY")
                                .url("https://www.theguardian.com/business/2026/may/22/fed-comments?utm_source=newsletter")
                                .publishedAt(LocalDateTime.of(2026, 5, 22, 21, 10))
                                .qualityStatus("SUMMARY_ONLY")
                                .isKapDisclosure(false)
                                .build()
                ))
        ));

        when(syncStateRepository.findById(anyString())).thenReturn(Optional.empty());
        when(newsRepository.findByExternalIdIn(anyCollection())).thenReturn(Set.of());
        when(newsRepository.findByUrlIn(anyCollection())).thenReturn(Set.of(
                News.builder()
                        .id(901L)
                        .externalId("GUARDIAN:old-1")
                        .title("Dollar and bond markets react to Fed comments")
                        .summary("Existing record")
                        .source("The Guardian")
                        .provider(NewsProviderType.GUARDIAN.name())
                        .language("en")
                        .regionScope("GLOBAL")
                        .category("INTEREST_BONDS")
                        .url("https://www.theguardian.com/business/2026/may/22/fed-comments")
                        .publishedAt(LocalDateTime.of(2026, 5, 22, 20, 55))
                        .build()
        ));
        when(newsRepository.findRecentPotentialDuplicates(anyCollection(), anyCollection(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        var response = service.syncProvider(NewsProviderType.GUARDIAN);

        verify(newsRepository, never()).saveAll(anyList());
        assertThat(response.getDuplicateCount()).isEqualTo(1);
        assertThat(response.getSavedCount()).isZero();
    }

    private NewsService buildService(
            NewsRepository newsRepository,
            NewsProviderSyncStateRepository syncStateRepository,
            List<NewsProvider> providers
    ) {
        MarketQueryService mqs = mock(MarketQueryService.class);
        com.emrehalli.financeportal.news.config.NewsRelationsProperties legacyProps =
                mock(com.emrehalli.financeportal.news.config.NewsRelationsProperties.class);
        when(legacyProps.isConservativeMode()).thenReturn(false);
        return new NewsService(
                newsRepository,
                syncStateRepository,
                providers,
                new NewsImportanceScoringService(),
                mock(NotificationService.class),
                mock(NewsNotificationProperties.class),
                new NewsPresentationMapper(),
                new NewsCategoryClassifier(),
                mqs,
                legacyProps,
                new ConservativeNewsRelationService(newsRepository, mqs)
        );
    }

    private record StubProvider(String providerName, List<NewsItemDto> latestItems) implements NewsProvider {
        @Override
        public String getProviderName() {
            return providerName;
        }

        @Override
        public List<NewsItemDto> fetchLatestNews() {
            return latestItems;
        }

        @Override
        public List<NewsItemDto> fetchCompanyNews(String symbol) {
            return latestItems;
        }
    }
}



