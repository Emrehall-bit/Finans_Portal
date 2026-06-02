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

        assertThat(classifier.classify(
                "Dolar/TL haftaya yukselisle basladi",
                "Kur cephesinde hareketlilik suruyor.",
                "Dolar/TL haftaya yukselisle basladi",
                "general"
        ).category()).isEqualTo(NewsCategoryClassifier.FX);

        assertThat(classifier.classify(
                "Finansal kesim disindaki firmalarin net doviz pozisyon acigi azaldi",
                "Net doviz pozisyonu ve yabanci para pozisyonu verileri aciklandi.",
                "Finansal kesim disindaki firmalarin net doviz pozisyon acigi azaldi",
                "general"
        ).category()).isEqualTo(NewsCategoryClassifier.FX);

        var bankingResult = classifier.classify(
                "Borsa Istanbul bankacilik hisseleriyle yukseldi",
                "Bankacilik hisseleri endeksi yukari tasidi.",
                "Borsa Istanbul bankacilik hisseleriyle yukseldi",
                "markets"
        );
        assertThat(bankingResult.category()).isIn(NewsCategoryClassifier.BANKING, NewsCategoryClassifier.STOCKS);

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
        ).category()).isEqualTo(NewsCategoryClassifier.GOLD_COMMODITY);

        var fedResult = classifier.classify(
                "Fed faiz indirimi sinyali verdi",
                "Kuresel piyasalar yeni faiz patikasini fiyatliyor. Dolar, altin ve tahvil cephesinde yeni denge aranÄ±yor.",
                "Fed faiz indirimi sinyali verdi",
                "top_news"
        );
        assertThat(fedResult.category()).isIn(NewsCategoryClassifier.INTEREST_BONDS, NewsCategoryClassifier.GLOBAL_MARKETS);

        var geopoliticsResult = classifier.classify(
                "ABD-Iran gerilimi petrol arzini etkiledi",
                "Petrol, doviz, altin ve hisse piyasalarinda risk algisi bozuldu.",
                "ABD-Iran gerilimi piyasalari etkiledi",
                "top_news"
        );
        assertThat(geopoliticsResult.category()).isIn(
                NewsCategoryClassifier.GOLD_COMMODITY,
                NewsCategoryClassifier.GLOBAL_MARKETS
        );
        assertThat(geopoliticsResult.category()).isNotEqualTo(NewsCategoryClassifier.GEOPOLITICS);
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
    void resolvesFilterCategoryWithoutAliasExpansion() {
        assertThat(classifier.resolveFilterCategories("ECONOMY"))
                .isEqualTo(Set.of("ECONOMY"));

        assertThat(classifier.resolveFilterCategories("business"))
                .isEqualTo(Set.of("BUSINESS"));

        assertThat(classifier.resolveFilterCategories("top_news"))
                .isEqualTo(Set.of("TOP_NEWS"));

        assertThat(classifier.resolveFilterCategories("FX"))
                .isEqualTo(Set.of("FX"));
    }

    @Test
    void keepsWeakContextStoriesOutOfBroadMarketCategories() {
        assertThat(classifier.classify(
                "Turizmde maliyetler ve tesvikler degerlendirildi",
                "Sektorde doviz bazinda maliyetler ve parite etkisi konusuldu.",
                "Turizmde maliyetler ve tesvikler degerlendirildi",
                "general"
        ).category()).isNotEqualTo(NewsCategoryClassifier.FX);

        assertThat(classifier.classify(
                "Bitkisel uretimin tahil ve meyvelerde artmasi bekleniyor",
                "Kuru meyve ve tarimsal uretim beklentileri aciklandi.",
                "Bitkisel uretimin tahil ve meyvelerde artmasi bekleniyor",
                "general"
        ).category()).isNotEqualTo(NewsCategoryClassifier.FX);

        var politicsResult = classifier.classify(
                "ABD heyeti siyasi temaslarda bulundu",
                "Siyasi gundem ve diplomatik gorusmeler one cikti.",
                "ABD heyeti siyasi temaslarda bulundu",
                "general"
        );
        assertThat(politicsResult.category()).isEqualTo(NewsCategoryClassifier.GENERAL_ECONOMY);

        var usMentionResult = classifier.classify(
                "ABD tarim raporu aciklandi",
                "Veri aciklamasi sonrasi istatistikler degerlendirildi.",
                "ABD tarim raporu aciklandi",
                "general"
        );
        assertThat(usMentionResult.category()).isEqualTo(NewsCategoryClassifier.GENERAL_ECONOMY);

        var companyResult = classifier.classify(
                "Sirket yeni lojistik merkezini acti",
                "Sirket operasyonel kapasitesini artiracak yatirim planini duyurdu.",
                "Sirket yeni lojistik merkezini acti",
                "company"
        );
        assertThat(companyResult.category()).isEqualTo(NewsCategoryClassifier.COMPANY);
    }

    @Test
    void classifiesStrongMarketContextByPrimaryCategory() {
        var energyResult = classifier.classify(
                "Petrol arz riski savas endisesiyle buyudu",
                "Savas, yaptirim ve enerji arz endiseleri brent fiyatlarini ve kuresel piyasalari baskiladi.",
                "Petrol arz riski savas endisesiyle buyudu",
                "top_news"
        );
        assertThat(energyResult.category()).isIn(
                NewsCategoryClassifier.GOLD_COMMODITY,
                NewsCategoryClassifier.GLOBAL_MARKETS
        );
        assertThat(energyResult.category()).isNotEqualTo(NewsCategoryClassifier.GEOPOLITICS);

        var tcmbResult = classifier.classify(
                "TCMB faiz karari sonrasi kur piyasasi hareketlendi",
                "Faiz karari sonrasi dolar/TL ve tahvil cephesinde oynaklik artti.",
                "TCMB faiz karari sonrasi kur piyasasi hareketlendi",
                "business"
        );
        assertThat(tcmbResult.category()).isEqualTo(NewsCategoryClassifier.INTEREST_BONDS);
    }

    @Test
    void requiresRealContextBeforeSelectingBroadMarketCategory() {
        var geopoliticalOnlyResult = classifier.classify(
                "Orta Dogu gerilimi ve savas endisesi suruyor",
                "Bolgedeki diplomatik gelismeler izleniyor.",
                "Orta Dogu gerilimi ve savas endisesi suruyor",
                "general"
        );
        assertThat(geopoliticalOnlyResult.category()).isEqualTo(NewsCategoryClassifier.GENERAL_ECONOMY);

        var politicsResult = classifier.classify(
                "ABD'de siyasi heyet yeni gorusme turuna basladi",
                "Diplomatik ziyaret ve siyasi temaslarin ayrintilari paylasildi.",
                "ABD'de siyasi heyet yeni gorusme turuna basladi",
                "general"
        );
        assertThat(politicsResult.category()).isEqualTo(NewsCategoryClassifier.GENERAL_ECONOMY);

        var usOnlyResult = classifier.classify(
                "ABD tarim verisi aciklandi",
                "Tarim istatistikleri ve yillik degisimler duyuruldu.",
                "ABD tarim verisi aciklandi",
                "general"
        );
        assertThat(usOnlyResult.category()).isEqualTo(NewsCategoryClassifier.GENERAL_ECONOMY);

        var companyOnlyResult = classifier.classify(
                "Sirket yeni depo yatirimini duyurdu",
                "Company yeni lojistik kapasitesi icin yatirim planini acikladi.",
                "Sirket yeni depo yatirimini duyurdu",
                "company"
        );
        assertThat(companyOnlyResult.category()).isEqualTo(NewsCategoryClassifier.COMPANY);
    }
}




