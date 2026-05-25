package com.emrehalli.financeportal.ai.news;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Resolves the probable sector impact, sentiment, and risk level for a detected news category.
 * Rules are intentionally heuristic: direction words (artış/indirim, hike/cut) guide branching
 * for categories where the sign of the event matters (interest rates, TCMB, Fed, earnings).
 */
@Component
public class SectorImpactResolver {

    public record SectorImpact(List<String> sectors, String sentiment, String riskLevel) {}

    public SectorImpact resolve(NewsCategory category, String title, String summary) {
        String combined = lower(title) + " " + lower(summary);
        return switch (category) {
            case OIL_ENERGY -> new SectorImpact(
                    List.of("HAVAYOLU → yakıt maliyeti artışı baskı oluşturabilir",
                            "ENERJİ → olumlu etkilenebilir"),
                    "MIXED", "MEDIUM"
            );
            case INTEREST_RATE -> resolveInterestRate(combined);
            case TCMB          -> resolveTcmb(combined);
            case FED           -> resolveFed(combined);
            case INFLATION -> new SectorImpact(
                    List.of("ÜRETİCİ MALİYETLERİ → artış baskısı oluşabilir",
                            "TÜKETİCİ SEKTÖRÜ → marj baskısı olabilir",
                            "SABİT GELİRLİ VARLIKLAR → olumsuz etkilenebilir"),
                    "NEGATIVE", "HIGH"
            );
            case DEFENSE -> new SectorImpact(
                    List.of("SAVUNMA ŞİRKETLERİ → olumlu etkilenebilir",
                            "BÜTÇE HARCAMALARI → savunma payı artabilir"),
                    "POSITIVE", "LOW"
            );
            case AVIATION -> new SectorImpact(
                    List.of("HAVAYOLU ŞİRKETLERİ → yakından etkilenebilir",
                            "TURİZM SEKTÖRÜ → dolaylı etki olabilir"),
                    "MIXED", "MEDIUM"
            );
            case BANKING -> new SectorImpact(
                    List.of("BANKALAR → yakından etkilenebilir",
                            "FİNANSAL SEKTÖR → genel etki değerlendirilebilir"),
                    "MIXED", "MEDIUM"
            );
            case CRYPTO -> new SectorImpact(
                    List.of("KRİPTO VARLIKLAR → yüksek volatilite olabilir",
                            "BLOCKCHAIN ŞİRKETLERİ → etkilenebilir"),
                    "MIXED", "HIGH"
            );
            case REGULATION -> new SectorImpact(
                    List.of("DÜZENLEMEYE TABİ SEKTÖRLER → risk içerebilir",
                            "UYUM MALİYETLERİ → artabilir"),
                    "MIXED", "MEDIUM"
            );
            case EARNINGS -> resolveEarnings(combined);
            case DIVIDEND -> new SectorImpact(
                    List.of("HİSSE SAHİPLERİ → temettü etkisi değerlendirilebilir",
                            "İLGİLİ HİSSE → fiyatlamaya yansıyabilir"),
                    "POSITIVE", "LOW"
            );
            case MERGER_ACQUISITION -> new SectorImpact(
                    List.of("İLGİLİ ŞİRKETLER → değerleme değişikliği olabilir",
                            "SEKTÖR YOĞUNLAŞMASI → etkilenebilir"),
                    "MIXED", "MEDIUM"
            );
            case INVESTMENT -> new SectorImpact(
                    List.of("FİNANSAL PİYASALAR → sermaye akışı etkilenebilir",
                            "İLGİLİ VARLIK SINIFI → olumlu etkilenebilir"),
                    "NEUTRAL", "LOW"
            );
            default -> new SectorImpact(
                    List.of("GENEL PİYASA → haberin kapsamına göre etkilenebilir"),
                    "NEUTRAL", "LOW"
            );
        };
    }

    private SectorImpact resolveInterestRate(String combined) {
        if (containsAny(combined, "artış", "artırdı", "yükseltildi", "yükseltme", "hike", "raise")) {
            return new SectorImpact(
                    List.of("BANKALAR → kısa vadede olumlu etkilenebilir",
                            "BÜYÜME HİSSELERİ → baskı altında kalabilir",
                            "TAHVIL PİYASASI → fiyatlar gerileyebilir"),
                    "MIXED", "MEDIUM"
            );
        }
        if (containsAny(combined, "indirim", "indirdi", "düşürdü", "düşüş", "cut", "lower")) {
            return new SectorImpact(
                    List.of("BÜYÜME HİSSELERİ → desteklenebilir",
                            "RİSK İŞTAHI → artabilir",
                            "TAHVIL PİYASASI → fiyatlar yükselebilir"),
                    "POSITIVE", "LOW"
            );
        }
        return new SectorImpact(
                List.of("FİNANSAL PİYASALAR → faiz kararı yön verebilir"),
                "NEUTRAL", "MEDIUM"
        );
    }

    private SectorImpact resolveTcmb(String combined) {
        if (containsAny(combined, "indirim", "indirdi", "düşürdü", "düşüş")) {
            return new SectorImpact(
                    List.of("BANKA DIŞI HİSSELER → olumlu etkilenebilir",
                            "RİSK İŞTAHI → artabilir",
                            "TL VARLIKLAR → baskılanabilir"),
                    "POSITIVE", "LOW"
            );
        }
        if (containsAny(combined, "artış", "artırdı", "yükseltildi", "yükseltme")) {
            return new SectorImpact(
                    List.of("TL VARLIKLAR → değer kazanabilir",
                            "BÜYÜME HİSSELERİ → baskılanabilir"),
                    "MIXED", "MEDIUM"
            );
        }
        return new SectorImpact(
                List.of("FİNANSAL VARLIKLAR → TCMB kararı yön verebilir"),
                "NEUTRAL", "MEDIUM"
        );
    }

    private SectorImpact resolveFed(String combined) {
        if (containsAny(combined, "hike", "raise", "artış", "yükseltme", "artırdı")) {
            return new SectorImpact(
                    List.of("KÜRESEL BÜYÜME HİSSELERİ → baskı altında kalabilir",
                            "DOLAR → güçlenebilir",
                            "GELİŞMEKTE OLAN PİYASALAR → baskılanabilir"),
                    "NEGATIVE", "HIGH"
            );
        }
        if (containsAny(combined, "cut", "lower", "indirim", "düşüş", "indirdi")) {
            return new SectorImpact(
                    List.of("KÜRESEL RİSK İŞTAHI → artabilir",
                            "GELİŞMEKTE OLAN PİYASALAR → desteklenebilir"),
                    "POSITIVE", "MEDIUM"
            );
        }
        return new SectorImpact(
                List.of("KÜRESEL PİYASALAR → Fed kararı belirleyici olabilir"),
                "NEUTRAL", "MEDIUM"
        );
    }

    private SectorImpact resolveEarnings(String combined) {
        boolean positive = containsAny(combined, "artış", "büyüme", "rekor", "aştı", "beklentinin üzerinde");
        boolean negative = containsAny(combined, "düşüş", "zarar", "kayıp", "beklentinin altında", "zayıf");
        String sentiment = positive ? "POSITIVE" : negative ? "NEGATIVE" : "MIXED";
        String risk      = positive ? "LOW"      : negative ? "HIGH"     : "MEDIUM";
        return new SectorImpact(
                List.of("İLGİLİ HİSSE → bilanço sonuçlarına göre fiyatlanabilir",
                        "SEKTÖR BENZERLERİ → yansıma olabilir"),
                sentiment, risk
        );
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private String lower(String s) {
        return s != null ? s.toLowerCase(Locale.ROOT) : "";
    }
}



