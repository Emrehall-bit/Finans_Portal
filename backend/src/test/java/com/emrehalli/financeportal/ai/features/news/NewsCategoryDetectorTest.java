package com.emrehalli.financeportal.ai.features.news;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewsCategoryDetectorTest {

    private final NewsCategoryDetector detector = new NewsCategoryDetector();
    private final SectorImpactResolver resolver = new SectorImpactResolver();

    @Test
    void detect_titleCryptoSignalWinsOverWrongAppCategory() {
        NewsCategoryDetector.DetectionResult result = detector.detectWithEvidence(
                "Bitcoin ve kripto piyasasında likidite artıyor",
                "Blockchain şirketlerine yönelik ilgi güçlendi.",
                "BANKING"
        );

        assertThat(result.category()).isEqualTo(NewsCategory.CRYPTO);
        assertThat(result.titleMatched()).isTrue();
        assertThat(result.categoryOnly()).isFalse();
    }

    @Test
    void detect_weakTitleUsesBankingSignalFromSummary() {
        NewsCategoryDetector.DetectionResult result = detector.detectWithEvidence(
                "Piyasalarda yeni gündem başlığı",
                "Bankacılık sektörü ve mevduat faizleri haber akışının merkezinde yer aldı.",
                "GENERAL_ECONOMY"
        );

        assertThat(result.category()).isEqualTo(NewsCategory.BANKING);
        assertThat(result.summaryMatched()).isTrue();
        assertThat(result.categoryOnly()).isFalse();
    }

    @Test
    void resolver_doesNotProduceCryptoAssetsWhenOnlyAppCategorySaysCrypto() {
        NewsCategoryDetector.DetectionResult result = detector.detectWithEvidence(
                "Piyasalarda yeni gündem başlığı",
                "Metinde belirgin finansal anahtar kelime bulunmuyor.",
                "CRYPTO"
        );

        SectorImpactResolver.SectorImpact impact = resolver.resolve(result, "Piyasalarda yeni gündem başlığı", "Metinde belirgin finansal anahtar kelime bulunmuyor.");

        assertThat(result.category()).isEqualTo(NewsCategory.CRYPTO);
        assertThat(result.categoryOnly()).isTrue();
        assertThat(impact.sectors()).isEmpty();
    }

    @Test
    void detect_generalAppCategoryCanBeOverriddenByContentSectorSignal() {
        NewsCategoryDetector.DetectionResult result = detector.detectWithEvidence(
                "Şirketlerden yeni gündem başlığı",
                "Savunma sanayi şirketleri için yeni ihale süreci takip ediliyor.",
                "GENERAL_ECONOMY"
        );

        SectorImpactResolver.SectorImpact impact = resolver.resolve(result, "Şirketlerden yeni gündem başlığı", "Savunma sanayi şirketleri için yeni ihale süreci takip ediliyor.");

        assertThat(result.category()).isEqualTo(NewsCategory.DEFENSE);
        assertThat(result.summaryMatched()).isTrue();
        assertThat(impact.sectors()).contains("Savunma sanayi şirketleri");
    }

    @Test
    void resolver_keepsAffectedAssetsEmptyForUnclearNews() {
        NewsCategoryDetector.DetectionResult result = detector.detectWithEvidence(
                "Gündemde öne çıkan başlıklar",
                "Açıklamada finansal piyasalar açısından belirgin bir sektör sinyali yer almıyor.",
                null
        );

        SectorImpactResolver.SectorImpact impact = resolver.resolve(result, "Gündemde öne çıkan başlıklar", "Açıklamada finansal piyasalar açısından belirgin bir sektör sinyali yer almıyor.");

        assertThat(result.category()).isEqualTo(NewsCategory.GENERAL);
        assertThat(impact.sectors()).isEmpty();
    }
}