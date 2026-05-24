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
    void tcmbRateNewsResolvesFxAndXu100ButNotBankStocks() {
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
                .contains("USDTRY", "EURTRY", "XU100");
        // Metinde şirket adı olmadığı için banka hisseleri çıkmamalı
        assertThat(response.relatedInstruments()).extracting("symbol")
                .doesNotContain("AKBNK", "GARAN", "ISCTR", "YKBNK");
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
    void bankingRegulationNewsDoesNotProduceBankStocksButShowsXu100() {
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

        // Sektörel bağlam → bireysel banka hissesi çıkmamalı (doğrudan metin eşleşmesi olmadan)
        assertThat(response.relatedInstruments()).extracting("symbol")
                .doesNotContain("AKBNK", "GARAN", "ISCTR", "YKBNK");
        // Bankacılık bağlamı → geniş endeks gösterilmeli
        assertThat(response.relatedInstruments()).extracting("symbol")
                .contains("XU100");
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

        // Sektörel bağlamda bireysel hisse yok (metin eşleşmesi olmadan)
        assertThat(response.relatedInstruments()).extracting("symbol")
                .doesNotContain("ASELS", "GOLD", "BRENT", "FROTO", "TOASO");
        // Sanayi/ihracat bağlamı → geniş endeks gösterilmeli
        assertThat(response.relatedInstruments()).extracting("symbol")
                .contains("XU100");
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
    void defenseNewsWithoutCompanyNameDoesNotProduceStock() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        // "Aselsan" adı metinde YOK – sektörel savunma bağlamı var
        News defenseNews = baseNews(570L,
                "Savunma ihalesinde askeri teknoloji ve fuze sistemi one cikti",
                "NATO uyumlu savunma harcamasi ve silah sistemi baglami piyasada izlendi.",
                "COMPANY",
                "STOCKS");

        when(newsRepository.findById(570L)).thenReturn(Optional.of(defenseNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(570L);

        // Şirket adı geçmediği için ASELS çıkmamalı
        assertThat(response.relatedInstruments()).extracting("symbol")
                .doesNotContain("ASELS");
    }

    @Test
    void warNewsProducesGoldButNotDefenseStock() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        // "Aselsan" adı metinde YOK; "altin" doğrudan var
        News warNews = baseNews(580L,
                "Orta Dogu'da savas ve askeri gerilim enerji arzini tehdit etti",
                "Jeopolitik risk ve guvenli liman talebi artarken savunma harcamalari ile altin fiyatlari izlendi.",
                "GLOBAL_MARKETS",
                "GLOBAL_MARKETS,ENERGY");

        when(newsRepository.findById(580L)).thenReturn(Optional.of(warNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(580L);

        // Altın: metinde doğrudan "altin" geçiyor → gösterilmeli
        assertThat(response.relatedInstruments()).extracting("symbol")
                .contains("GOLD");
        // ASELS: metinde şirket adı yok → gösterilmemeli
        assertThat(response.relatedInstruments()).extracting("symbol")
                .doesNotContain("ASELS");
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

        News genericNews = baseNews(900L,
                "Genel ekonomi ve sirket yatirimlari konusuldu",
                "Ekonomi, ihracat ve sanayi gundemi degerlendirildi.",
                "GENERAL_ECONOMY",
                "GENERAL_ECONOMY");
        // Metinde "altin" ve "dolar" geçiyor → doğrudan enstrüman eşleşmesi → HIGH güven
        News fxGoldNews = baseNews(901L,
                "TCMB kararinin ardından altin ve dolar fiyatlandi",
                "Merkez bankasi aciklamasi sonrasinda altin ve dolar kuru yeni seviyelere ulasti.",
                "INTEREST_BONDS",
                "INTEREST_BONDS,FX");

        when(newsRepository.findRecentNormalNews(any(Pageable.class)))
                .thenReturn(List.of(genericNews, fxGoldNews));

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

    @Test
    void tcmbPureRateNewsWithNoBankingLanguageDoesNotProduceBankStocks() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        // Haberde bankacılık bağlamı YOK – sadece saf faiz/TCMB makro bağlamı
        News tcmbPureNews = baseNews(210L,
                "TCMB politika faizini sabit tuttu",
                "Merkez bankasi politika faizini degistirmedi. Dolar kuru ve piyasalar tepkisini bekliyor.",
                "INTEREST_BONDS",
                "INTEREST_BONDS,FX");

        when(newsRepository.findById(210L)).thenReturn(Optional.of(tcmbPureNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(210L);

        // Makro enstrümanlar gösterilmeli
        assertThat(response.relatedInstruments()).extracting("symbol")
                .contains("USDTRY", "EURTRY");
        // Bankacılık dili olmadığı için banka hisseleri çıkmamalı
        assertThat(response.relatedInstruments()).extracting("symbol")
                .doesNotContain("AKBNK", "GARAN", "ISCTR", "YKBNK");
        // Makro-only limit: max 3
        assertThat(response.relatedInstruments()).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void goldNewsOnlyShowsGoldNoStocks() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News goldNews = baseNews(220L,
                "Altin fiyatlari rekor kırdi, ons altin yukseliyor",
                "Guvenli liman talebi arttikca altin piyasasinda yukselis surdü.",
                "GOLD_COMMODITY",
                "GOLD_COMMODITY");

        when(newsRepository.findById(220L)).thenReturn(Optional.of(goldNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(220L);

        assertThat(response.relatedInstruments()).extracting("symbol").contains("GOLD");
        assertThat(response.relatedInstruments()).extracting("symbol")
                .doesNotContain("AKBNK", "GARAN", "ISCTR", "YKBNK", "THYAO", "PGSUS");
    }

    @Test
    void pureEnergyNewsWithoutCompanyNamesOnlyShowsBrent() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        // Haberde THYAO/PGSUS/TUPRS geçmiyor
        News energyNews = baseNews(230L,
                "Brent petrol 90 dolara yaklasti, enerji arzi endiseleri gundemde",
                "OPEC uretim kisintisi sonrasinda petrol fiyatlari yukseldi. Enerji piyasasi riski artıyor.",
                "GOLD_COMMODITY",
                "GOLD_COMMODITY,GLOBAL_MARKETS");

        when(newsRepository.findById(230L)).thenReturn(Optional.of(energyNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(230L);

        assertThat(response.relatedInstruments()).extracting("symbol").contains("BRENT");
        // Şirket adı geçmediği için bu hisseler çıkmamalı
        assertThat(response.relatedInstruments()).extracting("symbol")
                .doesNotContain("TUPRS", "THYAO", "PGSUS");
    }

    @Test
    void kapThyaoNewsShowsThyaoDirectlyWithMaxTwoInstruments() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News kapNews = News.builder()
                .id(240L)
                .externalId("KAP-240")
                .title("Turk Hava Yollari kargo kapasitesini artirma karari aldi")
                .summary("THY yonetim kurulu kargo filosunu genisletme kararini KAP'a bildirdi.")
                .source("KAP")
                .provider("KAP")
                .language("tr")
                .regionScope("DOMESTIC")
                .category("STOCKS")
                .filterTags("STOCKS")
                .url("https://kap.org.tr/240")
                .relatedSymbol("THYAO")
                .isKapDisclosure(true)
                .publishedAt(LocalDateTime.of(2026, 5, 23, 9, 0))
                .importanceScore(90)
                .build();

        when(newsRepository.findById(240L)).thenReturn(Optional.of(kapNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(240L);

        assertThat(response.relatedInstruments()).extracting("symbol").containsExactly("THYAO");
        assertThat(response.relatedInstruments().get(0).confidence()).isEqualTo("HIGH");
        assertThat(response.relatedInstruments()).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void relatedNewsOnlyCategoryOverlapIsRejected() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News mainNews = baseNews(610L,
                "TCMB faiz karari piyasalari hareklendirdi, dolar yukseldi",
                "Merkez bankasi faiz kararinin ardindan dolar kuru ve altin fiyatlari reaksiyon verdi.",
                "INTEREST_BONDS",
                "INTEREST_BONDS,FX,GOLD_COMMODITY");
        mainNews.setPublishedAt(LocalDateTime.of(2026, 5, 23, 10, 0));

        // Sadece aynı kategoride, institution/commodity/company overlap yok
        News weakCandidate = baseNews(611L,
                "Enflasyon beklentileri karısık seyretti, analistler tartisiyor",
                "Ekonomistler genel enflasyon gorunumunu degerlendirdi.",
                "INTEREST_BONDS",
                "INTEREST_BONDS");
        weakCandidate.setPublishedAt(LocalDateTime.of(2026, 5, 23, 8, 0));

        when(newsRepository.findById(610L)).thenReturn(Optional.of(mainNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of(weakCandidate));

        NewsRelatedResponseDto response = service.getRelatedData(610L);

        // Sadece kategori örtüşmesi yetmemeli
        assertThat(response.relatedNews()).isEmpty();
    }

    @Test
    void relatedNewsWithMacroTopicOverlapPasses() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        News mainNews = baseNews(620L,
                "Fed faiz indirimi sinyali verdi, dolar ve altin fiyatlandi",
                "Fed aciklamasi sonrasinda altin ve dolar piyasalarinda yeni fiyatlama goruldu.",
                "INTEREST_BONDS",
                "INTEREST_BONDS,FX,GOLD_COMMODITY");
        mainNews.setPublishedAt(LocalDateTime.of(2026, 5, 23, 10, 0));

        // Aynı makro konu (Fed/faiz/altin) → geçmeli
        News strongCandidate = baseNews(621L,
                "Fed uyeleri faiz ve altin patikasini tartisiyor",
                "Altin fiyatlari ve tahvil piyasasi Fed mesajlari sonrasinda hareketlendi.",
                "INTEREST_BONDS",
                "INTEREST_BONDS,FX,GOLD_COMMODITY");
        strongCandidate.setPublishedAt(LocalDateTime.of(2026, 5, 22, 14, 0));

        when(newsRepository.findById(620L)).thenReturn(Optional.of(mainNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of(strongCandidate));

        NewsRelatedResponseDto response = service.getRelatedData(620L);

        assertThat(response.relatedNews()).hasSize(1);
        assertThat(response.relatedNews().get(0).id()).isEqualTo(621L);
    }

    @Test
    void machineExportNewsDoesNotProduceBankOrDefenseStocks() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        // Makine ihracatı/imalat/finansman/kur bağlamı – hiçbir şirket adı geçmiyor
        News machineExportNews = baseNews(630L,
                "Makine Ihracatcilari Birligi rekor ihracat rakamini acikladi",
                "Imalat sanayinde finansman kanallari ve kur baskisi nedeniyle ihracat gelirleri tartisiliyor.",
                "GENERAL_ECONOMY",
                "GENERAL_ECONOMY,STOCKS");

        when(newsRepository.findById(630L)).thenReturn(Optional.of(machineExportNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(630L);

        // Şirket adı geçmediği için hiçbir bireysel hisse çıkmamalı
        assertThat(response.relatedInstruments()).extracting("symbol")
                .doesNotContain("GARAN", "AKBNK", "ISCTR", "YKBNK",
                        "ASELS", "THYAO", "PGSUS", "TUPRS",
                        "FROTO", "TOASO");
        // Kur bağlamı → USDTRY/EURTRY/XU100 görünebilir (broad enstrümanlar)
        if (!response.relatedInstruments().isEmpty()) {
            assertThat(response.relatedInstruments()).extracting("symbol")
                    .allMatch(sym -> "XU100".equals(sym) || "USDTRY".equals(sym) || "EURTRY".equals(sym)
                            || "GOLD".equals(sym) || "BRENT".equals(sym));
        }
    }

    @Test
    void newsWithExplicitAselsamTextShowsAselsAsDirectMatch() {
        NewsRepository newsRepository = mock(NewsRepository.class);
        MarketQueryService marketQueryService = mock(MarketQueryService.class);
        NewsService service = buildService(newsRepository, marketQueryService);

        // Metinde "Aselsan" doğrudan geçiyor → DIRECT/HIGH eşleşmesi
        News aselsamNews = baseNews(640L,
                "Aselsan yeni ihracat sozlesmesini duyurdu",
                "Aselsan savunma elektroniği sistemlerinin ihracatı için kapsamlı anlaşma imzaladı.",
                "COMPANY",
                "STOCKS");

        when(newsRepository.findById(640L)).thenReturn(Optional.of(aselsamNews));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        NewsRelatedResponseDto response = service.getRelatedData(640L);

        // Metinde doğrudan "Aselsan" geçiyor → ASELS görünmeli
        assertThat(response.relatedInstruments()).extracting("symbol")
                .contains("ASELS");
        // Doğrudan eşleşme → confidence HIGH
        assertThat(response.relatedInstruments())
                .anyMatch(item -> "ASELS".equals(item.symbol()) && "HIGH".equals(item.confidence()));
    }

    private NewsService buildService(NewsRepository newsRepository, MarketQueryService marketQueryService) {
        NewsProviderSyncStateRepository syncStateRepository = mock(NewsProviderSyncStateRepository.class);
        NewsImportanceScoringService scoringService = mock(NewsImportanceScoringService.class);
        NotificationService notificationService = mock(NotificationService.class);
        NewsNotificationProperties notificationProperties = mock(NewsNotificationProperties.class);

        com.emrehalli.financeportal.news.config.NewsRelationsProperties legacyProps =
                mock(com.emrehalli.financeportal.news.config.NewsRelationsProperties.class);
        when(legacyProps.isConservativeMode()).thenReturn(false);

        ConservativeNewsRelationService conservativeService =
                new ConservativeNewsRelationService(newsRepository, marketQueryService);

        return new NewsService(
                newsRepository,
                syncStateRepository,
                List.of(),
                scoringService,
                notificationService,
                notificationProperties,
                new NewsPresentationMapper(),
                new NewsCategoryClassifier(),
                marketQueryService,
                legacyProps,
                conservativeService
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
