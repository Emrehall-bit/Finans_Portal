package com.emrehalli.financeportal.ai.features.news;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Resolves the probable sector impact, sentiment, and risk level for a detected news category.
 * Rules are intentionally heuristic: direction words (artÄ±ÅŸ/indirim, hike/cut) guide branching
 * for categories where the sign of the event matters (interest rates, TCMB, Fed, earnings).
 */
@Component
public class SectorImpactResolver {

    public record SectorImpact(List<String> sectors, String sentiment, String riskLevel) {}

    public SectorImpact resolve(NewsCategory category, String title, String summary) {
        String combined = lower(title) + " " + lower(summary);
        return switch (category) {
            case OIL_ENERGY -> new SectorImpact(
                    List.of("HAVAYOLU â†’ yakÄ±t maliyeti artÄ±ÅŸÄ± baskÄ± oluÅŸturabilir",
                            "ENERJÄ° â†’ olumlu etkilenebilir"),
                    "MIXED", "MEDIUM"
            );
            case INTEREST_RATE -> resolveInterestRate(combined);
            case TCMB          -> resolveTcmb(combined);
            case FED           -> resolveFed(combined);
            case INFLATION -> new SectorImpact(
                    List.of("ÃœRETÄ°CÄ° MALÄ°YETLERÄ° â†’ artÄ±ÅŸ baskÄ±sÄ± oluÅŸabilir",
                            "TÃœKETÄ°CÄ° SEKTÃ–RÃœ â†’ marj baskÄ±sÄ± olabilir",
                            "SABÄ°T GELÄ°RLÄ° VARLIKLAR â†’ olumsuz etkilenebilir"),
                    "NEGATIVE", "HIGH"
            );
            case DEFENSE -> new SectorImpact(
                    List.of("SAVUNMA ÅÄ°RKETLERÄ° â†’ olumlu etkilenebilir",
                            "BÃœTÃ‡E HARCAMALARI â†’ savunma payÄ± artabilir"),
                    "POSITIVE", "LOW"
            );
            case AVIATION -> new SectorImpact(
                    List.of("HAVAYOLU ÅÄ°RKETLERÄ° â†’ yakÄ±ndan etkilenebilir",
                            "TURÄ°ZM SEKTÃ–RÃœ â†’ dolaylÄ± etki olabilir"),
                    "MIXED", "MEDIUM"
            );
            case BANKING -> new SectorImpact(
                    List.of("BANKALAR â†’ yakÄ±ndan etkilenebilir",
                            "FÄ°NANSAL SEKTÃ–R â†’ genel etki deÄŸerlendirilebilir"),
                    "MIXED", "MEDIUM"
            );
            case CRYPTO -> new SectorImpact(
                    List.of("KRÄ°PTO VARLIKLAR â†’ yÃ¼ksek volatilite olabilir",
                            "BLOCKCHAIN ÅÄ°RKETLERÄ° â†’ etkilenebilir"),
                    "MIXED", "HIGH"
            );
            case REGULATION -> new SectorImpact(
                    List.of("DÃœZENLEMEYE TABÄ° SEKTÃ–RLER â†’ risk iÃ§erebilir",
                            "UYUM MALÄ°YETLERÄ° â†’ artabilir"),
                    "MIXED", "MEDIUM"
            );
            case EARNINGS -> resolveEarnings(combined);
            case DIVIDEND -> new SectorImpact(
                    List.of("HÄ°SSE SAHÄ°PLERÄ° â†’ temettÃ¼ etkisi deÄŸerlendirilebilir",
                            "Ä°LGÄ°LÄ° HÄ°SSE â†’ fiyatlamaya yansÄ±yabilir"),
                    "POSITIVE", "LOW"
            );
            case MERGER_ACQUISITION -> new SectorImpact(
                    List.of("Ä°LGÄ°LÄ° ÅÄ°RKETLER â†’ deÄŸerleme deÄŸiÅŸikliÄŸi olabilir",
                            "SEKTÃ–R YOÄUNLAÅMASI â†’ etkilenebilir"),
                    "MIXED", "MEDIUM"
            );
            case INVESTMENT -> new SectorImpact(
                    List.of("FÄ°NANSAL PÄ°YASALAR â†’ sermaye akÄ±ÅŸÄ± etkilenebilir",
                            "Ä°LGÄ°LÄ° VARLIK SINIFI â†’ olumlu etkilenebilir"),
                    "NEUTRAL", "LOW"
            );
            default -> new SectorImpact(
                    List.of("GENEL PÄ°YASA â†’ haberin kapsamÄ±na gÃ¶re etkilenebilir"),
                    "NEUTRAL", "LOW"
            );
        };
    }

    private SectorImpact resolveInterestRate(String combined) {
        if (containsAny(combined, "artÄ±ÅŸ", "artÄ±rdÄ±", "yÃ¼kseltildi", "yÃ¼kseltme", "hike", "raise")) {
            return new SectorImpact(
                    List.of("BANKALAR â†’ kÄ±sa vadede olumlu etkilenebilir",
                            "BÃœYÃœME HÄ°SSELERÄ° â†’ baskÄ± altÄ±nda kalabilir",
                            "TAHVIL PÄ°YASASI â†’ fiyatlar gerileyebilir"),
                    "MIXED", "MEDIUM"
            );
        }
        if (containsAny(combined, "indirim", "indirdi", "dÃ¼ÅŸÃ¼rdÃ¼", "dÃ¼ÅŸÃ¼ÅŸ", "cut", "lower")) {
            return new SectorImpact(
                    List.of("BÃœYÃœME HÄ°SSELERÄ° â†’ desteklenebilir",
                            "RÄ°SK Ä°ÅTAHI â†’ artabilir",
                            "TAHVIL PÄ°YASASI â†’ fiyatlar yÃ¼kselebilir"),
                    "POSITIVE", "LOW"
            );
        }
        return new SectorImpact(
                List.of("FÄ°NANSAL PÄ°YASALAR â†’ faiz kararÄ± yÃ¶n verebilir"),
                "NEUTRAL", "MEDIUM"
        );
    }

    private SectorImpact resolveTcmb(String combined) {
        if (containsAny(combined, "indirim", "indirdi", "dÃ¼ÅŸÃ¼rdÃ¼", "dÃ¼ÅŸÃ¼ÅŸ")) {
            return new SectorImpact(
                    List.of("BANKA DIÅI HÄ°SSELER â†’ olumlu etkilenebilir",
                            "RÄ°SK Ä°ÅTAHI â†’ artabilir",
                            "TL VARLIKLAR â†’ baskÄ±lanabilir"),
                    "POSITIVE", "LOW"
            );
        }
        if (containsAny(combined, "artÄ±ÅŸ", "artÄ±rdÄ±", "yÃ¼kseltildi", "yÃ¼kseltme")) {
            return new SectorImpact(
                    List.of("TL VARLIKLAR â†’ deÄŸer kazanabilir",
                            "BÃœYÃœME HÄ°SSELERÄ° â†’ baskÄ±lanabilir"),
                    "MIXED", "MEDIUM"
            );
        }
        return new SectorImpact(
                List.of("FÄ°NANSAL VARLIKLAR â†’ TCMB kararÄ± yÃ¶n verebilir"),
                "NEUTRAL", "MEDIUM"
        );
    }

    private SectorImpact resolveFed(String combined) {
        if (containsAny(combined, "hike", "raise", "artÄ±ÅŸ", "yÃ¼kseltme", "artÄ±rdÄ±")) {
            return new SectorImpact(
                    List.of("KÃœRESEL BÃœYÃœME HÄ°SSELERÄ° â†’ baskÄ± altÄ±nda kalabilir",
                            "DOLAR â†’ gÃ¼Ã§lenebilir",
                            "GELÄ°ÅMEKTE OLAN PÄ°YASALAR â†’ baskÄ±lanabilir"),
                    "NEGATIVE", "HIGH"
            );
        }
        if (containsAny(combined, "cut", "lower", "indirim", "dÃ¼ÅŸÃ¼ÅŸ", "indirdi")) {
            return new SectorImpact(
                    List.of("KÃœRESEL RÄ°SK Ä°ÅTAHI â†’ artabilir",
                            "GELÄ°ÅMEKTE OLAN PÄ°YASALAR â†’ desteklenebilir"),
                    "POSITIVE", "MEDIUM"
            );
        }
        return new SectorImpact(
                List.of("KÃœRESEL PÄ°YASALAR â†’ Fed kararÄ± belirleyici olabilir"),
                "NEUTRAL", "MEDIUM"
        );
    }

    private SectorImpact resolveEarnings(String combined) {
        boolean positive = containsAny(combined, "artÄ±ÅŸ", "bÃ¼yÃ¼me", "rekor", "aÅŸtÄ±", "beklentinin Ã¼zerinde");
        boolean negative = containsAny(combined, "dÃ¼ÅŸÃ¼ÅŸ", "zarar", "kayÄ±p", "beklentinin altÄ±nda", "zayÄ±f");
        String sentiment = positive ? "POSITIVE" : negative ? "NEGATIVE" : "MIXED";
        String risk      = positive ? "LOW"      : negative ? "HIGH"     : "MEDIUM";
        return new SectorImpact(
                List.of("Ä°LGÄ°LÄ° HÄ°SSE â†’ bilanÃ§o sonuÃ§larÄ±na gÃ¶re fiyatlanabilir",
                        "SEKTÃ–R BENZERLERÄ° â†’ yansÄ±ma olabilir"),
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




