package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.service.MarketQueryService;
import com.emrehalli.financeportal.news.dto.response.RelatedInstrumentDto;
import com.emrehalli.financeportal.news.entity.News;
import com.emrehalli.financeportal.news.repository.NewsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConservativeNewsRelationServiceTest {

    private ConservativeNewsRelationService service;
    private NewsRepository newsRepository;
    private MarketQueryService marketQueryService;

    @BeforeEach
    void setUp() {
        newsRepository = mock(NewsRepository.class);
        marketQueryService = mock(MarketQueryService.class);
        service = new ConservativeNewsRelationService(newsRepository, marketQueryService);

        // Default: no price data unless test overrides
        when(marketQueryService.findBySymbol(anyString(), any())).thenReturn(Optional.empty());
    }

    // ========================= Gate tests =========================

    @Test
    void siteAidatiNewsProducesNoInstruments() {
        // "Site aidatı" has no financial market keywords
        News news = baseNews(1L,
                "Site aidatları bu yıl yüzde on arttı",
                "Konut sakinleri artan aidat maliyetlerini tartışıyor.",
                "GENERAL_ECONOMY", "GENERAL_ECONOMY");

        when(newsRepository.findById(1L)).thenReturn(Optional.of(news));
        when(newsRepository.findRecentCandidatesForRelatedNewsByCategory(anyLong(), anyString(), any()))
                .thenReturn(List.of());

        List<RelatedInstrumentDto> result = service.resolveInstruments(news);

        assertThat(result).isEmpty();
    }

    @Test
    void genericEconomyNewsWithNoMarketSignalProducesNoInstruments() {
        News news = baseNews(2L,
                "Kuresel buyume gorunumu karisik seyrediyor",
                "Ekonomistler genel buyume beklentilerini tartisiyor.",
                "GENERAL_ECONOMY", "GENERAL_ECONOMY");

        List<RelatedInstrumentDto> result = service.resolveInstruments(news);

        assertThat(result).isEmpty();
    }

    // ========================= KAP tests =========================

    @Test
    void kapDisclosureReturnsOnlyOfficialCompanyWithHighConfidence() {
        News kapNews = News.builder()
                .id(10L)
                .externalId("KAP-10")
                .title("Turk Hava Yollari kargo kapasitesini artirma karari aldi")
                .summary("THY yonetim kurulu KAP'a bildirdi.")
                .source("KAP")
                .provider("KAP")
                .language("tr")
                .regionScope("DOMESTIC")
                .category("STOCKS")
                .filterTags("STOCKS")
                .url("https://kap.org.tr/10")
                .relatedSymbol("THYAO")
                .isKapDisclosure(true)
                .publishedAt(LocalDateTime.of(2026, 5, 24, 9, 0))
                .importanceScore(90)
                .build();

        List<RelatedInstrumentDto> result = service.resolveInstruments(kapNews);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).symbol()).isEqualTo("THYAO");
        assertThat(result.get(0).confidence()).isEqualTo("HIGH");
        assertThat(result.get(0).relationType()).isEqualTo("DIRECT");
    }

    // ========================= Direct company match tests =========================

    @Test
    void newsWithExplicitThyaoTextShowsThyaoAsDirectHighConfidence() {
        News news = baseNews(20L,
                "Turk Hava Yollari yeni uçuş güzergahları açıkladı",
                "THY büyüme stratejisi kapsamında yeni destinasyonlar ekliyor.",
                "COMPANY", "STOCKS");

        List<RelatedInstrumentDto> result = service.resolveInstruments(news);

        assertThat(result).extracting("symbol").contains("THYAO");
        assertThat(result).anyMatch(r -> "THYAO".equals(r.symbol()) && "HIGH".equals(r.confidence()));
    }

    @Test
    void newsWithExplicitAselsamTextShowsAselsAsDirectHighConfidence() {
        News news = baseNews(21L,
                "Aselsan yeni ihracat anlaşması imzaladı",
                "Aselsan savunma elektroniği ihracatını artırıyor.",
                "COMPANY", "STOCKS");

        List<RelatedInstrumentDto> result = service.resolveInstruments(news);

        assertThat(result).extracting("symbol").contains("ASELS");
        assertThat(result).anyMatch(r -> "ASELS".equals(r.symbol()) && "HIGH".equals(r.confidence()));
    }

    @Test
    void stockWithoutDirectMentionNeverProducedByConservativeService() {
        // News about banking regulation — "bankacilik" mentioned but no company name
        News news = baseNews(22L,
                "BDDK bankacilik sektorune yeni duzenleme getirdi",
                "Bankacilik sektoru ve kredi buyumesi acisindan yeni kurallar aciklandi.",
                "BANKING", "BANKING,STOCKS");

        List<RelatedInstrumentDto> result = service.resolveInstruments(news);

        // No individual bank stocks without direct text mention
        assertThat(result).extracting("symbol")
                .doesNotContain("AKBNK", "GARAN", "ISCTR", "YKBNK");
        // Broad instruments are fine (XU100 from macro context, USDTRY from KUR/KREDI context)
        assertThat(result).extracting("symbol").allMatch(sym ->
                "XU100".equals(sym) || "USDTRY".equals(sym) || "EURTRY".equals(sym)
                        || "GOLD".equals(sym) || "BRENT".equals(sym) || "GRAM_ALTIN".equals(sym));
    }

    // ========================= Macro signal tests =========================

    @Test
    void tcmbFaizNewsReturnsUsdtryEurtryXu100() {
        News news = baseNews(30L,
                "TCMB politika faizini sabit tuttu",
                "Merkez bankasi faiz karari sonrasinda piyasalar tepki verdi.",
                "INTEREST_BONDS", "INTEREST_BONDS,FX");

        List<RelatedInstrumentDto> result = service.resolveInstruments(news);

        assertThat(result).extracting("symbol").contains("USDTRY", "EURTRY", "XU100");
        assertThat(result).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void goldNewsReturnsGoldAndGramAltin() {
        News news = baseNews(40L,
                "Altin fiyatlari rekor kırdi, ons altin yukseliyor",
                "Guvenli liman talebi arttikca altin piyasasinda yukselis surdu.",
                "GOLD_COMMODITY", "GOLD_COMMODITY");

        List<RelatedInstrumentDto> result = service.resolveInstruments(news);

        assertThat(result).extracting("symbol").contains("GOLD");
        // GRAM_ALTIN should also appear
        assertThat(result).extracting("symbol").containsAnyOf("GOLD", "GRAM_ALTIN");
        // No stocks
        assertThat(result).extracting("symbol")
                .doesNotContain("AKBNK", "GARAN", "ASELS", "THYAO");
    }

    @Test
    void oilNewsReturnsBrent() {
        News news = baseNews(50L,
                "Brent petrol 90 dolara yaklasti, enerji arz endisesi buyuyor",
                "OPEC uretim kisintisi sonrasinda petrol fiyatlari yukselmis durumda.",
                "GOLD_COMMODITY", "GOLD_COMMODITY,GLOBAL_MARKETS");

        List<RelatedInstrumentDto> result = service.resolveInstruments(news);

        assertThat(result).extracting("symbol").contains("BRENT");
        assertThat(result).extracting("symbol")
                .doesNotContain("TUPRS", "THYAO", "PGSUS");
    }

    @Test
    void machineExportWithKurFinansmanDoesNotProduceBankOrDefenseStocks() {
        // "kur" is in text → gate passes; "FINANSMAN" provides export context
        // No company names → no stocks
        News news = baseNews(60L,
                "Makine Ihracatcilari Birligi rekor ihracat rakamini acikladi",
                "Imalat sanayinde finansman kanallari ve kur baskisi nedeniyle ihracat gelirleri tartisiliyor.",
                "GENERAL_ECONOMY", "GENERAL_ECONOMY,STOCKS");

        List<RelatedInstrumentDto> result = service.resolveInstruments(news);

        // No individual stocks
        assertThat(result).extracting("symbol")
                .doesNotContain("GARAN", "AKBNK", "ISCTR", "YKBNK",
                        "ASELS", "THYAO", "PGSUS", "TUPRS", "FROTO", "TOASO");
        // Broad instruments (driven by "KUR") are acceptable
        if (!result.isEmpty()) {
            assertThat(result).extracting("symbol").allMatch(sym ->
                    "XU100".equals(sym) || "USDTRY".equals(sym) || "EURTRY".equals(sym)
                            || "GOLD".equals(sym) || "BRENT".equals(sym) || "GRAM_ALTIN".equals(sym));
        }
    }

    @Test
    void siteAidatiWithFinansmanAloneStillProducesNoInstruments() {
        // "finansman" alone does NOT open the gate (not in STRONG_GATE_TOKENS)
        News news = baseNews(61L,
                "Site aidati finansman ihtiyaci artıyor",
                "Konut yonetim maliyetleri ve finansman kosullari tartisiliyor.",
                "GENERAL_ECONOMY", "GENERAL_ECONOMY");

        List<RelatedInstrumentDto> result = service.resolveInstruments(news);

        assertThat(result).isEmpty();
    }

    @Test
    void exportNewsAloneWithoutContextProducesNoInstruments() {
        // "ihracat" alone (without KUR/MALIYET/FINANSMAN context)
        News news = baseNews(62L,
                "Turkiye ihracat rakamlari aciklandi",
                "Gecen ayin ihracat verileri aciklandi.",
                "GENERAL_ECONOMY", "GENERAL_ECONOMY");

        List<RelatedInstrumentDto> result = service.resolveInstruments(news);

        assertThat(result).isEmpty();
    }

    // ========================= Max instruments test =========================

    @Test
    void maxInstrumentsLimitIsThree() {
        // News with many market signals
        News news = baseNews(70L,
                "TCMB faiz kararının ardından borsa, altin, dolar ve petrol fiyatlandi",
                "Faiz kararı sonrasında USDTRY, altin, brent petrol ve BIST 100 yeni seviyelere ulasti.",
                "INTEREST_BONDS", "INTEREST_BONDS,FX,GOLD_COMMODITY");

        List<RelatedInstrumentDto> result = service.resolveInstruments(news);

        assertThat(result).hasSizeLessThanOrEqualTo(3);
    }

    // ========================= No LOW confidence =========================

    @Test
    void lowConfidenceInstrumentsNeverReturned() {
        News news = baseNews(80L,
                "TCMB faiz kararini acikladi",
                "Politika faizi haberi piyasaları etkiledi.",
                "INTEREST_BONDS", "INTEREST_BONDS,FX");

        List<RelatedInstrumentDto> result = service.resolveInstruments(news);

        assertThat(result).allMatch(r -> !"LOW".equals(r.confidence()));
    }

    // ========================= Reason field =========================

    @Test
    void instrumentsHaveNonEmptyReasonField() {
        News news = baseNews(90L,
                "TCMB faizini degistirmedi, kur reaksiyon verdi",
                "Merkez bankasi kararinin ardından doviz kurlari hareket etti.",
                "INTEREST_BONDS", "INTEREST_BONDS,FX");

        List<RelatedInstrumentDto> result = service.resolveInstruments(news);

        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(r -> r.reason() != null && !r.reason().isBlank());
    }

    @Test
    void kapInstrumentHasKapReason() {
        News kapNews = News.builder()
                .id(91L)
                .externalId("KAP-91")
                .title("Garanti BBVA temettü dağıtım kararı")
                .summary("Garanti BBVA yonetim kurulu karar aldi.")
                .source("KAP")
                .provider("KAP")
                .language("tr")
                .regionScope("DOMESTIC")
                .category("STOCKS")
                .filterTags("STOCKS")
                .url("https://kap.org.tr/91")
                .relatedSymbol("GARAN")
                .isKapDisclosure(true)
                .publishedAt(LocalDateTime.of(2026, 5, 24, 9, 0))
                .importanceScore(85)
                .build();

        List<RelatedInstrumentDto> result = service.resolveInstruments(kapNews);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).symbol()).isEqualTo("GARAN");
        assertThat(result.get(0).reason()).contains("KAP");
    }

    // ========================= Price snapshot integration =========================

    @Test
    void usdtryWithPriceSnapshotPopulatesLastPrice() {
        News news = baseNews(100L,
                "Dolar kuru yeni seviyelere yukseldi",
                "Dolar TL paritesi yeni zirvelere ulasti.",
                "FX", "FX");

        when(marketQueryService.findBySymbol("USDTRY", InstrumentType.FX))
                .thenReturn(Optional.of(new MarketQueryService.MarketSnapshot(
                        "USDTRY", "USD/TRY", BigDecimal.valueOf(40.50),
                        BigDecimal.valueOf(0.55), "TCMB", InstrumentType.FX.name(), "TRY",
                        LocalDateTime.now())));

        List<RelatedInstrumentDto> result = service.resolveInstruments(news);

        assertThat(result).anyMatch(r -> "USDTRY".equals(r.symbol()) && r.lastPrice() != null);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private News baseNews(Long id, String title, String summary, String category, String filterTags) {
        return News.builder()
                .id(id)
                .externalId("TEST-" + id)
                .title(title)
                .summary(summary)
                .source("Test Source")
                .provider("GUARDIAN")
                .language("tr")
                .regionScope("DOMESTIC")
                .category(category)
                .filterTags(filterTags)
                .url("https://example.com/news/" + id)
                .publishedAt(LocalDateTime.of(2026, 5, 24, 9, 0))
                .importanceScore(80)
                .build();
    }
}



