package com.emrehalli.financeportal.ai.features.news;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Resolves broad asset/sector buckets for news impact analysis.
 * Assets are produced only when the detected category is supported by title or summary evidence.
 */
@Component
public class SectorImpactResolver {

    public record SectorImpact(List<String> sectors) {}

    public SectorImpact resolve(NewsCategoryDetector.DetectionResult detection, String title, String summary) {
        if (detection == null || !detection.hasContentEvidence() || detection.category() == NewsCategory.GENERAL) {
            return new SectorImpact(List.of());
        }
        return resolve(detection.category(), title, summary);
    }

    public SectorImpact resolve(NewsCategory category, String title, String summary) {
        String combined = lower(title) + " " + lower(summary);
        return switch (category) {
            case OIL_ENERGY -> new SectorImpact(List.of("Enerji şirketleri", "Havayolu şirketleri", "Emtia piyasası"));
            case INTEREST_RATE -> resolveInterestRate(combined);
            case TCMB -> resolveTcmb(combined);
            case FED -> resolveFed(combined);
            case INFLATION -> new SectorImpact(List.of("Tüketici şirketleri", "Sabit getirili varlıklar"));
            case DEFENSE -> new SectorImpact(List.of("Savunma sanayi şirketleri", "BIST sanayi hisseleri"));
            case AVIATION -> new SectorImpact(List.of("Havayolu şirketleri", "Turizm sektörü"));
            case BANKING -> new SectorImpact(List.of("Bankalar", "Finansal sektör"));
            case CRYPTO -> new SectorImpact(List.of("Kripto varlıklar", "Blockchain şirketleri"));
            case REGULATION -> new SectorImpact(List.of("Düzenlemeye tabi sektörler", "FinTech şirketleri"));
            case EARNINGS -> new SectorImpact(List.of("İlgili hisse", "Sektör benzerleri"));
            case DIVIDEND -> new SectorImpact(List.of("İlgili hisse", "Temettü hisseleri"));
            case MERGER_ACQUISITION -> new SectorImpact(List.of("İlgili şirketler", "Sektör benzerleri"));
            case INVESTMENT -> new SectorImpact(List.of("İlgili varlık sınıfı"));
            default -> new SectorImpact(List.of());
        };
    }

    private SectorImpact resolveInterestRate(String combined) {
        if (containsAny(combined, "artış", "artırdı", "yükseltildi", "yükseltme", "hike", "raise")) {
            return new SectorImpact(List.of("Bankalar", "Büyüme hisseleri", "Tahvil piyasası"));
        }
        if (containsAny(combined, "indirim", "indirdi", "düşürdü", "düşüş", "cut", "lower")) {
            return new SectorImpact(List.of("Büyüme hisseleri", "Riskli varlıklar", "Tahvil piyasası"));
        }
        return new SectorImpact(List.of("Finansal piyasalar", "Faize duyarlı varlıklar"));
    }

    private SectorImpact resolveTcmb(String combined) {
        if (containsAny(combined, "indirim", "indirdi", "düşürdü", "düşüş")) {
            return new SectorImpact(List.of("TL varlıklar", "BIST hisseleri", "Döviz piyasası"));
        }
        if (containsAny(combined, "artış", "artırdı", "yükseltildi", "yükseltme")) {
            return new SectorImpact(List.of("TL varlıklar", "Büyüme hisseleri", "Tahvil piyasası"));
        }
        return new SectorImpact(List.of("TL varlıklar", "Finansal piyasalar"));
    }

    private SectorImpact resolveFed(String combined) {
        if (containsAny(combined, "hike", "raise", "artış", "yükseltme", "artırdı")) {
            return new SectorImpact(List.of("Küresel hisse piyasaları", "Döviz piyasası", "Gelişmekte olan piyasalar"));
        }
        if (containsAny(combined, "cut", "lower", "indirim", "düşüş", "indirdi")) {
            return new SectorImpact(List.of("Küresel riskli varlıklar", "Gelişmekte olan piyasalar"));
        }
        return new SectorImpact(List.of("Küresel piyasalar", "Döviz piyasası"));
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String lower(String s) {
        return s != null ? s.toLowerCase(Locale.ROOT) : "";
    }
}