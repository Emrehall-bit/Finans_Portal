package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.admin.notification.service.NotificationService;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import com.emrehalli.financeportal.news.config.NewsNotificationProperties;
import com.emrehalli.financeportal.news.dto.response.NewsAffectedInstrumentsAuditResponseDto;
import com.emrehalli.financeportal.news.dto.response.NewsRelatedResponseDto;
import com.emrehalli.financeportal.news.entity.News;
import com.emrehalli.financeportal.news.repository.NewsProviderSyncStateRepository;
import com.emrehalli.financeportal.news.repository.NewsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsServiceRelatedDataTest {

    @Test
    void fedNewsResolvesUsdTryGoldAndXu100() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News fedNews = baseNews(100L,
                "Fed faiz indirimi sinyali verdi, dolar ve piyasalarda oynaklik arttı",
                "Fed karari sonrasinda dolar kuru, altin ve kuresel piyasalar yeni fiyatlama arayisina girdi.",
                "INTEREST_BONDS",
                "INTEREST_BONDS,FX,GOLD_COMMODITY,GLOBAL_MARKETS");

        when(newsRepository.findById(100L)).thenReturn(Optional.of(fedNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(marketQueryService.findBySymbol("USDTRY", InstrumentType.FX))
                .thenReturn(Optional.of(new MarketQueryService.MarketSnapshot(
                        "USDTRY", "USD/TRY", BigDecimal.valueOf(39.12), BigDecimal.valueOf(0.84), "TCMB", InstrumentType.FX.name(), "TRY", LocalDateTime.now()
                )));

        NewsRelatedResponseDto response = service.getRelatedData(100L);

        assertThat(response.relatedInstruments()).extracting("symbol")
                .contains("USDTRY", "GOLD", "XU100");
        assertThat(response.relatedInstruments()).anyMatch(item ->
                "USDTRY".equals(item.symbol()) && "HIGH".equals(item.confidence()) && item.lastPrice() != null);
    }

    @Test
    void tcmbRateNewsResolvesFxBankingAndXu100() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News tcmbNews = baseNews(200L,
                "TCMB faiz kararini acikladi, kur ve bankacilik hisseleri izleniyor",
                "Politika faizi karari sonrasinda dolar/TL, euro/TL ve banka hisseleri gundemde.",
                "INTEREST_BONDS",
                "INTEREST_BONDS,FX,BANKING,GLOBAL_MARKETS");

        when(newsRepository.findById(200L)).thenReturn(Optional.of(tcmbNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(200L);

        assertThat(response.relatedInstruments()).extracting("symbol")
                .contains("USDTRY", "EURTRY");
        assertThat(response.relatedInstruments()).extracting("symbol")
                .anyMatch(symbol ->
                        "XU100".equals(symbol)
                                || "AKBNK".equals(symbol)
                                || "GARAN".equals(symbol)
                                || "ISCTR".equals(symbol)
                                || "YKBNK".equals(symbol));
    }

    @Test
    void middleEastOilNewsResolvesBrentTuprasAndAirlines() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News oilNews = baseNews(300L,
                "Orta Dogu gerilimi petrol arz endisesini artırdı",
                "Brent petrol yukselirken OPEC kaynakli arz riski THYAO, Pegasus ve Tupras icin kritik izleniyor.",
                "GLOBAL_MARKETS",
                "GLOBAL_MARKETS,GOLD_COMMODITY,FX");

        when(newsRepository.findById(300L)).thenReturn(Optional.of(oilNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(300L);

        assertThat(response.relatedInstruments()).extracting("symbol")
                .contains("BRENT", "TUPRS");
        assertThat(response.relatedInstruments()).extracting("symbol")
                .anyMatch(symbol -> "THYAO".equals(symbol) || "PGSUS".equals(symbol));
    }

    @Test
    void bankingRegulationNewsResolvesBankShares() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News regulationNews = baseNews(400L,
                "BDDK bankacilik sektorune yonelik yeni kredi duzenlemesini duyurdu",
                "Bankacilik sektoru ve kredi buyumesi acisindan yeni regulasyonlar aciklandi.",
                "BANKING",
                "BANKING,STOCKS,GENERAL_ECONOMY");

        when(newsRepository.findById(400L)).thenReturn(Optional.of(regulationNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(400L);

        assertThat(response.relatedInstruments()).extracting("symbol")
                .containsAnyOf("AKBNK", "GARAN", "ISCTR", "YKBNK");
        assertThat(response.relatedInstruments()).extracting("symbol")
                .doesNotContain("ASELS", "GOLD");
        assertThat(response.relatedInstruments()).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void genericEconomyNewsDoesNotOverproduceIrrelevantInstruments() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News genericNews = baseNews(500L,
                "Kuresel buyume gorunumu yil sonu tahminlerinde karisik seyrediyor",
                "Ekonomistler buyume beklentilerini ve genel ekonomik gorunumu tartisiyor.",
                "GENERAL_ECONOMY",
                "GENERAL_ECONOMY");

        when(newsRepository.findById(500L)).thenReturn(Optional.of(genericNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(500L);

        assertThat(response.relatedInstruments()).isEmpty();
    }

    @Test
    void relatedNewsUsesCategoryTagsInstitutionsAndCommoditySignals() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News mainNews = baseNews(600L,
                "Fed faiz indirimi sinyali verdi, altin ve dolar yeniden fiyatlandi",
                "Fed aciklamasi sonrasinda altin, dolar ve tahvil piyasalarinda yeni fiyatlama goruldu.",
                "INTEREST_BONDS",
                "INTEREST_BONDS,FX,GOLD_COMMODITY,GLOBAL_MARKETS");
        mainNews.setPublishedAt(LocalDateTime.of(2026, 5, 23, 10, 0));

        News strongCandidate = baseNews(601L,
                "Fed uyeleri sonraki toplantida faiz ve dolar patikasini tartisiyor",
                "Altin fiyatlari ve tahvil piyasasi Fed mesajlari sonrasinda hareketlendi.",
                "INTEREST_BONDS",
                "INTEREST_BONDS,FX,GOLD_COMMODITY,GLOBAL_MARKETS");
        strongCandidate.setPublishedAt(LocalDateTime.of(2026, 5, 22, 13, 0));

        News weakCandidate = baseNews(602L,
                "Perakende satis verileri genel ekonomiye dair karisik sinyal verdi",
                "Ic talep ve tuketim verileri aciklandi.",
                "GENERAL_ECONOMY",
                "GENERAL_ECONOMY");
        weakCandidate.setPublishedAt(LocalDateTime.of(2026, 5, 22, 12, 0));

        when(newsRepository.findById(600L)).thenReturn(Optional.of(mainNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of(strongCandidate, weakCandidate));

        NewsRelatedResponseDto response = service.getRelatedData(600L);

        assertThat(response.relatedNews()).hasSize(1);
        assertThat(response.relatedNews().get(0).id()).isEqualTo(601L);
    }

    @Test
    void nullCategoryUsesCategorylessCandidateQueryAndReturnsEmptyResponse() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News categorylessNews = baseNews(700L,
                "Makro gorunumde yeni beklentiler aciklandi",
                "Piyasa katilimcilari buyume ve enflasyon dengesini izliyor.",
                null,
                "GENERAL_ECONOMY");

        when(newsRepository.findById(700L)).thenReturn(Optional.of(categorylessNews));
        when(newsRepository.findRecentCandidatesForRelatedNews(anyLong(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(700L);

        assertThat(response).isNotNull();
        assertThat(response.relatedNews()).isEmpty();
        assertThat(response.relatedInstruments()).isEmpty();
    }

    @Test
    void automotiveExportNewsDoesNotProduceDefenseOrSafeHavenNoise() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News exportNews = baseNews(550L,
                "Sakarya'dan yilin ilk 4 ayinda 48 bin arac ihrac edildi",
                "Otomotiv sanayi uretim, ihracat ve tedarik performansiyla dikkat cekti.",
                "GENERAL_ECONOMY",
                "GENERAL_ECONOMY,STOCKS");

        when(newsRepository.findById(550L)).thenReturn(Optional.of(exportNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(550L);

        assertThat(response.relatedInstruments()).extracting("symbol")
                .doesNotContain("ASELS", "GOLD", "BRENT");
        assertThat(response.relatedInstruments()).extracting("symbol")
                .containsAnyOf("FROTO", "TOASO");
        assertThat(response.relatedInstruments()).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void otokarExportNewsUsesAutomotiveDominanceInsteadOfDefense() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News otokarNews = baseNews(560L,
                "Otokar savunma sirketlerinden biri olarak arac uretimi ve ihracatini artirdi",
                "Otomotiv, fabrika, kamyon ve ihracat performansi sirketin sanayi uretiminde one cikti.",
                "COMPANY",
                "STOCKS,GENERAL_ECONOMY");

        when(newsRepository.findById(560L)).thenReturn(Optional.of(otokarNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(560L);

        assertThat(response.relatedInstruments()).extracting("symbol")
                .contains("OTKAR")
                .doesNotContain("ASELS", "GOLD");
    }

    @Test
    void defenseTenderNewsCanStillProduceAsels() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News defenseNews = baseNews(570L,
                "Savunma ihalesinde askeri teknoloji ve fuze sistemi one cikti",
                "NATO uyumlu savunma harcamasi ve silah sistemi baglami piyasada izlendi.",
                "COMPANY",
                "STOCKS");

        when(newsRepository.findById(570L)).thenReturn(Optional.of(defenseNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(570L);

        assertThat(response.relatedInstruments()).extracting("symbol")
                .contains("ASELS");
    }

    @Test
    void warNewsCanProduceDefenseAndGold() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News warNews = baseNews(580L,
                "Orta Dogu'da savas ve askeri gerilim enerji arzini tehdit etti",
                "Jeopolitik risk ve guvenli liman talebi artarken savunma harcamalari ile altin fiyatlari izlendi.",
                "GLOBAL_MARKETS",
                "GLOBAL_MARKETS,ENERGY");

        when(newsRepository.findById(580L)).thenReturn(Optional.of(warNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(580L);

        assertThat(response.relatedInstruments()).extracting("symbol")
                .contains("ASELS", "GOLD");
    }

    @Test
    void genericIndustrialNewsDoesNotProduceDefense() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News industrialNews = baseNews(590L,
                "Sanayi uretimi ve fabrika kapasite kullanimi yukselis gosterdii",
                "Ihracat, tedarik ve yatirim harcamalari imalat sanayinde takip ediliyor.",
                "GENERAL_ECONOMY",
                "GENERAL_ECONOMY");

        when(newsRepository.findById(590L)).thenReturn(Optional.of(industrialNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(590L);

        assertThat(response.relatedInstruments()).extracting("symbol")
                .doesNotContain("ASELS", "GOLD");
    }

    @Test
    void relatedDataFallsBackToEmptyListsWhenInstrumentOrCandidateLookupFails() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News news = baseNews(800L,
                "Fed faiz karari sonrasi piyasa hareketlendi",
                "Dolar/TL ve altin fiyatlamasi izleniyor.",
                "INTEREST_BONDS",
                "INTEREST_BONDS,FX,GOLD_COMMODITY");

        when(newsRepository.findById(800L)).thenReturn(Optional.of(news));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("query failed"));
        when(marketQueryService.findBySymbol("USDTRY", InstrumentType.FX))
                .thenThrow(new RuntimeException("snapshot failed"));

        NewsRelatedResponseDto response = service.getRelatedData(800L);

        assertThat(response).isNotNull();
        assertThat(response.relatedNews()).isEmpty();
        assertThat(response.relatedInstruments()).isEmpty();
    }

    @Test
    void affectedInstrumentsAuditSummarizesRecentNews() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News suspiciousNews = baseNews(900L,
                "Genel ekonomi ve sirket yatirimlari konusuldu",
                "Ekonomi, ihracat ve sanayi gundemi degerlendirildi.",
                "GENERAL_ECONOMY",
                "GENERAL_ECONOMY");
        News defenseNews = baseNews(901L,
                "Savunma ihalesinde askeri teknoloji ve fuze sistemi one cikti",
                "NATO uyumlu savunma harcamasi ve silah sistemi baglami piyasada izlendi.",
                "COMPANY",
                "STOCKS");

        when(newsRepository.findRecentNormalNews(any(Pageable.class)))
                .thenReturn(List.of(suspiciousNews, defenseNews));

        NewsAffectedInstrumentsAuditResponseDto response = service.auditAffectedInstruments(100);

        assertThat(response.checkedCount()).isEqualTo(2);
        assertThat(response.emptyCount()).isGreaterThanOrEqualTo(1);
        assertThat(response.mediumConfidenceCount() + response.highConfidenceCount()).isGreaterThanOrEqualTo(1);
        assertThat(response.suspiciousItems()).isNotNull();
    }

    @Test
    void affectedInstrumentsAuditTracksEmptyItems() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News genericNews = baseNews(910L,
                "Makro tahminler karisik seyretti",
                "Ekonomistler genel gorunumu degerlendirdi.",
                "GENERAL_ECONOMY",
                "GENERAL_ECONOMY");

        when(newsRepository.findRecentNormalNews(any(Pageable.class)))
                .thenReturn(List.of(genericNews));

        NewsAffectedInstrumentsAuditResponseDto response = service.auditAffectedInstruments(10);

        assertThat(response.checkedCount()).isEqualTo(1);
        assertThat(response.emptyCount()).isEqualTo(1);
    }

    private NewsService buildService(NewsRepository newsRepository, MarketQueryService marketQueryService) {
        NewsProviderSyncStateRepository syncStateRepository = mock(NewsProviderSyncStateRepository.class);
        NewsImportanceScoringService scoringService = mock(NewsImportanceScoringService.class);
        NotificationService notificationService = mock(NotificationService.class);
        NewsNotificationProperties notificationProperties = mock(NewsNotificationProperties.class);

        return new NewsService(
                newsRepository,
                syncStateRepository,
                List.of(),
                scoringService,
                notificationService,
                notificationProperties,
                new NewsPresentationMapper(),
                new NewsCategoryClassifier(),
                marketQueryService
        );
    }

    private News baseNews(Long id, String title, String summary, String category, String filterTags) {
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
                .filterTags(filterTags)
                .url("https://example.com/news/" + id)
                .publishedAt(LocalDateTime.of(2026, 5, 23, 9, 0))
                .importanceScore(80)
                .build();
    }
}
