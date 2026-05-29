package com.emrehalli.financeportal.news.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NewsCategoryClassifierTest {

    private final NewsCategoryClassifier classifier = new NewsCategoryClassifier();

    @Test
    void classifiesPrimaryTaxonomyExamples() {
        var tcmbResult = classifier.classify(
                "TCMB faiz kararini acikladi",
                "Karar tahvil ve doviz piyasasinda yakindan izlendi, kur cephesinde yeni fiyatlama olustu.",
                "TCMB faiz kararini acikladi",
                "business"
        );
        assertThat(tcmbResult.category()).isEqualTo(NewsCategoryClassifier.INTEREST_BONDS);
        assertThat(tcmbResult.tags()).contains(NewsCategoryClassifier.INTEREST_BONDS, NewsCategoryClassifier.FX);

        assertThat(classifier.classify(
                "Dolar/TL haftaya yukselisle basladi",
                "Kur cephesinde hareketlilik suruyor.",
                "Dolar/TL haftaya yukselisle basladi",
                "general"
        ).category()).isEqualTo(NewsCategoryClassifier.FX);

        var bankingResult = classifier.classify(
                "Borsa Istanbul bankacilik hisseleriyle yukseldi",
                "Bankacilik hisseleri endeksi yukari tasidi.",
                "Borsa Istanbul bankacilik hisseleriyle yukseldi",
                "markets"
        );
        assertThat(bankingResult.category()).isIn(NewsCategoryClassifier.BANKING, NewsCategoryClassifier.STOCKS);
        assertThat(bankingResult.tags()).contains(NewsCategoryClassifier.BANKING, NewsCategoryClassifier.STOCKS);

        assertThat(classifier.classify(
                "Altin ons fiyati guclendi",
                "Emtia cephesinde altin talebi artti.",
                "Altin ons fiyati guclendi",
                "commodity"
        ).category()).isEqualTo(NewsCategoryClassifier.GOLD_COMMODITY);

        assertThat(classifier.classify(
                "Petrol arz endisesi artti",
                "Enerji arzi ve brent fiyatlari yeniden gundemde.",
                "Petrol arz endisesi artti",
                "general"
        ).category()).isEqualTo(NewsCategoryClassifier.ENERGY);

        var fedResult = classifier.classify(
                "Fed faiz indirimi sinyali verdi",
                "Kuresel piyasalar yeni faiz patikasini fiyatliyor. Dolar, altin ve tahvil cephesinde yeni denge aranÄ±yor.",
                "Fed faiz indirimi sinyali verdi",
                "top_news"
        );
        assertThat(fedResult.category()).isIn(NewsCategoryClassifier.INTEREST_BONDS, NewsCategoryClassifier.GLOBAL_MARKETS);
        assertThat(fedResult.tags()).contains(
                NewsCategoryClassifier.INTEREST_BONDS,
                NewsCategoryClassifier.FX,
                NewsCategoryClassifier.GOLD_COMMODITY,
                NewsCategoryClassifier.GLOBAL_MARKETS
        );

        var geopoliticsResult = classifier.classify(
                "ABD-Iran gerilimi petrol arzini etkiledi",
                "Petrol, doviz, altin ve hisse piyasalarinda risk algisi bozuldu.",
                "ABD-Iran gerilimi piyasalari etkiledi",
                "top_news"
        );
        assertThat(geopoliticsResult.category()).isIn(
                NewsCategoryClassifier.ENERGY,
                NewsCategoryClassifier.GEOPOLITICS,
                NewsCategoryClassifier.GLOBAL_MARKETS
        );
        assertThat(geopoliticsResult.tags()).contains(
                NewsCategoryClassifier.ENERGY,
                NewsCategoryClassifier.GLOBAL_MARKETS,
                NewsCategoryClassifier.GOLD_COMMODITY,
                NewsCategoryClassifier.FX,
                NewsCategoryClassifier.STOCKS
        );
    }

    @Test
    void rejectsPurePoliticsAndSportsContent() {
        assertThat(classifier.classify(
                "Siyasi parti aciklama yapti",
                "Parti sozcusu secim kampanyasi ve meclis gundemine dair aciklama yapti.",
                "Siyasi parti aciklama yapti",
                "general"
        ).rejectReason()).isEqualTo("REJECT_NON_FINANCE_POLITICS");

        assertThat(classifier.classify(
                "Futbol transfer haberi",
                "Takim yeni oyuncu transferini duyurdu.",
                "Futbol transfer haberi",
                "sports"
        ).rejectReason()).isEqualTo("REJECT_NON_FINANCE_TOPIC");
    }

    @Test
    void resolvesLegacyFilterValuesWithoutBreakingExistingQueries() {
        assertThat(classifier.resolveFilterCategories("ECONOMY"))
                .contains("ECONOMY", NewsCategoryClassifier.GENERAL_ECONOMY);

        assertThat(classifier.resolveFilterCategories("business"))
                .contains("BUSINESS", NewsCategoryClassifier.GENERAL_ECONOMY, NewsCategoryClassifier.COMPANY);

        assertThat(classifier.resolveFilterCategories("top_news"))
                .contains("TOP_NEWS", NewsCategoryClassifier.GLOBAL_MARKETS, NewsCategoryClassifier.STOCKS);

        assertThat(classifier.resolveFilterCategories("FX"))
                .isEqualTo(Set.of("FX", "FOREX", "CURRENCY", "DOVIZ"));
    }

    @Test
    void resolvesFilterKeywordFallbacksForCanonicalFilters() {
        assertThat(classifier.resolveFilterRuntimeKeywords(NewsCategoryClassifier.INTEREST_BONDS))
                .contains("faiz", "tahvil", "fed");

        assertThat(classifier.resolveFilterRuntimeKeywords(NewsCategoryClassifier.FX))
                .contains("dolar tl", "parite", "forex");

        assertThat(classifier.resolveFilterRuntimeKeywords(NewsCategoryClassifier.GOLD_COMMODITY))
                .contains("altin", "commodity", "petrol");
    }

    @Test
    void avoidsOverTaggingWeakContextStories() {
        var politicsResult = classifier.classify(
                "ABD heyeti siyasi temaslarda bulundu",
                "Siyasi gundem ve diplomatik gorusmeler one cikti.",
                "ABD heyeti siyasi temaslarda bulundu",
                "general"
        );
        assertThat(politicsResult.tags()).doesNotContain(NewsCategoryClassifier.FX, NewsCategoryClassifier.GOLD_COMMODITY);

        var usMentionResult = classifier.classify(
                "ABD tarim raporu aciklandi",
                "Veri aciklamasi sonrasi istatistikler degerlendirildi.",
                "ABD tarim raporu aciklandi",
                "general"
        );
        assertThat(usMentionResult.tags()).doesNotContain(NewsCategoryClassifier.GLOBAL_MARKETS);

        var companyResult = classifier.classify(
                "Sirket yeni lojistik merkezini acti",
                "Sirket operasyonel kapasitesini artiracak yatirim planini duyurdu.",
                "Sirket yeni lojistik merkezini acti",
                "company"
        );
        assertThat(companyResult.category()).isEqualTo(NewsCategoryClassifier.COMPANY);
        assertThat(companyResult.tags()).doesNotContain(NewsCategoryClassifier.STOCKS);
    }

    @Test
    void keepsHighPrecisionCrossMarketTagsWhenContextIsStrong() {
        var energyResult = classifier.classify(
                "Petrol arz riski savas endisesiyle buyudu",
                "Savas, yaptirim ve enerji arz endiseleri brent fiyatlarini ve kuresel piyasalari baskiladi.",
                "Petrol arz riski savas endisesiyle buyudu",
                "top_news"
        );
        assertThat(energyResult.tags()).contains(
                NewsCategoryClassifier.ENERGY,
                NewsCategoryClassifier.GOLD_COMMODITY,
                NewsCategoryClassifier.GLOBAL_MARKETS
        );

        var tcmbResult = classifier.classify(
                "TCMB faiz karari sonrasi kur piyasasi hareketlendi",
                "Faiz karari sonrasi dolar/TL ve tahvil cephesinde oynaklik artti.",
                "TCMB faiz karari sonrasi kur piyasasi hareketlendi",
                "business"
        );
        assertThat(tcmbResult.tags()).contains(NewsCategoryClassifier.INTEREST_BONDS, NewsCategoryClassifier.FX);
    }

    @Test
    void requiresRealContextBeforeAddingBroadMarketTags() {
        var politicsResult = classifier.classify(
                "ABD'de siyasi heyet yeni gorusme turuna basladi",
                "Diplomatik ziyaret ve siyasi temaslarin ayrintilari paylasildi.",
                "ABD'de siyasi heyet yeni gorusme turuna basladi",
                "general"
        );
        assertThat(politicsResult.tags()).doesNotContain(
                NewsCategoryClassifier.FX,
                NewsCategoryClassifier.GOLD_COMMODITY,
                NewsCategoryClassifier.GLOBAL_MARKETS
        );

        var usOnlyResult = classifier.classify(
                "ABD tarim verisi aciklandi",
                "Tarim istatistikleri ve yillik degisimler duyuruldu.",
                "ABD tarim verisi aciklandi",
                "general"
        );
        assertThat(usOnlyResult.tags()).doesNotContain(NewsCategoryClassifier.GLOBAL_MARKETS);

        var companyOnlyResult = classifier.classify(
                "Sirket yeni depo yatirimini duyurdu",
                "Company yeni lojistik kapasitesi icin yatirim planini acikladi.",
                "Sirket yeni depo yatirimini duyurdu",
                "company"
        );
        assertThat(companyOnlyResult.category()).isEqualTo(NewsCategoryClassifier.COMPANY);
        assertThat(companyOnlyResult.tags()).doesNotContain(NewsCategoryClassifier.STOCKS);
    }
}




