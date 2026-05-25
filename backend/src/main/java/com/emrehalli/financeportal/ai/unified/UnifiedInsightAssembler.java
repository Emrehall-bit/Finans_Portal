package com.emrehalli.financeportal.ai.unified;

import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse;
import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse.FinancialHealth;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse.AiSignal;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Assembles separate technical and fundamental responses into a single
 * {@link UnifiedAnalysisContext}, with a derived reasoning note that
 * explicitly describes the alignment (or conflict) between the two views.
 */
@Component
public class UnifiedInsightAssembler {

    public UnifiedAnalysisContext assemble(String symbol,
                                           AiTechnicalAnalysisResponse technical,
                                           AiFundamentalAnalysisResponse fundamental) {
        boolean hasFund = fundamental != null;

        return new UnifiedAnalysisContext(
                symbol,
                // Technical
                technical.summary(),
                technical.trendComment(),
                technical.momentumComment(),
                signalLabel(technical.signal()),
                // Fundamental
                hasFund,
                hasFund ? fundamental.summary() : null,
                hasFund ? nullSafe(fundamental.strengths()) : List.of(),
                hasFund ? nullSafe(fundamental.weaknesses()) : List.of(),
                hasFund ? nullSafe(fundamental.risks()) : List.of(),
                hasFund ? fundamental.growthComment() : null,
                hasFund ? healthLabel(fundamental.financialHealth()) : null,
                // Reasoning
                buildConflictNote(technical, fundamental)
        );
    }

    // ── Conflict reasoning ────────────────────────────────────────

    String buildConflictNote(AiTechnicalAnalysisResponse tech, AiFundamentalAnalysisResponse fund) {
        if (fund == null) {
            return "Temel analiz bu enstrüman tipi için geçerli değil; yorum yalnızca teknik veriler üzerine kurulu.";
        }

        boolean techPositive = tech.signal() == AiSignal.POSITIVE;
        boolean techNegative = tech.signal() == AiSignal.NEGATIVE || tech.signal() == AiSignal.RISKY;
        boolean fundStrong   = fund.financialHealth() == FinancialHealth.STRONG
                             || fund.financialHealth() == FinancialHealth.STABLE;
        boolean fundRisky    = fund.financialHealth() == FinancialHealth.RISKY;

        if (techPositive && fundRisky) {
            return "Kısa vadeli momentum olumlu olsa da temel tarafta bazı riskler devam ediyor.";
        }
        if (techNegative && fundStrong) {
            return "Temel görünüm olumlu olsa da teknik momentum tarafı kısa vadede zayıf kalıyor. "
                 + "Uzun vadeli görünüm kısa vadeye göre daha güçlü olabilir.";
        }
        if (techPositive && fundStrong) {
            return "Teknik ve temel görünüm birbirini destekler nitelikte görünüyor.";
        }
        if (techNegative && fundRisky) {
            return "Hem teknik hem temel tarafta olumsuz işaretler mevcut; "
                 + "risk yönetimi öncelikli olabilir.";
        }
        return "Teknik ve temel veriler karma sinyaller üretiyor; iki tarafı birlikte değerlendirmek önemli.";
    }

    // ── Label helpers ─────────────────────────────────────────────

    private String signalLabel(AiSignal signal) {
        if (signal == null) return "Belirsiz";
        return switch (signal) {
            case POSITIVE -> "Pozitif";
            case NEGATIVE -> "Negatif";
            case RISKY    -> "Riskli";
            case NEUTRAL  -> "Nötr";
        };
    }

    private String healthLabel(FinancialHealth health) {
        if (health == null) return "Belirsiz";
        return switch (health) {
            case STRONG -> "Güçlü";
            case STABLE -> "Dengeli";
            case WATCH  -> "İzleme";
            case RISKY  -> "Riskli";
        };
    }

    private List<String> nullSafe(List<String> list) {
        return list != null ? list : List.of();
    }
}



