package com.emrehalli.financeportal.ai.features.unified;

import com.emrehalli.financeportal.ai.features.fundamental.AiFundamentalAnalysisResponse;
import com.emrehalli.financeportal.ai.features.fundamental.AiFundamentalAnalysisResponse.FinancialHealth;
import com.emrehalli.financeportal.ai.features.technical.AiTechnicalAnalysisResponse;
import com.emrehalli.financeportal.ai.features.technical.AiTechnicalAnalysisResponse.AiSignal;
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

    // â”€â”€ Conflict reasoning â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    String buildConflictNote(AiTechnicalAnalysisResponse tech, AiFundamentalAnalysisResponse fund) {
        if (fund == null) {
            return "Temel analiz bu enstrÃ¼man tipi iÃ§in geÃ§erli deÄŸil; yorum yalnÄ±zca teknik veriler Ã¼zerine kurulu.";
        }

        boolean techPositive = tech.signal() == AiSignal.POSITIVE;
        boolean techNegative = tech.signal() == AiSignal.NEGATIVE || tech.signal() == AiSignal.RISKY;
        boolean fundStrong   = fund.financialHealth() == FinancialHealth.STRONG
                             || fund.financialHealth() == FinancialHealth.STABLE;
        boolean fundRisky    = fund.financialHealth() == FinancialHealth.RISKY;

        if (techPositive && fundRisky) {
            return "KÄ±sa vadeli momentum olumlu olsa da temel tarafta bazÄ± riskler devam ediyor.";
        }
        if (techNegative && fundStrong) {
            return "Temel gÃ¶rÃ¼nÃ¼m olumlu olsa da teknik momentum tarafÄ± kÄ±sa vadede zayÄ±f kalÄ±yor. "
                 + "Uzun vadeli gÃ¶rÃ¼nÃ¼m kÄ±sa vadeye gÃ¶re daha gÃ¼Ã§lÃ¼ olabilir.";
        }
        if (techPositive && fundStrong) {
            return "Teknik ve temel gÃ¶rÃ¼nÃ¼m birbirini destekler nitelikte gÃ¶rÃ¼nÃ¼yor.";
        }
        if (techNegative && fundRisky) {
            return "Hem teknik hem temel tarafta olumsuz iÅŸaretler mevcut; "
                 + "risk yÃ¶netimi Ã¶ncelikli olabilir.";
        }
        return "Teknik ve temel veriler karma sinyaller Ã¼retiyor; iki tarafÄ± birlikte deÄŸerlendirmek Ã¶nemli.";
    }

    // â”€â”€ Label helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private String signalLabel(AiSignal signal) {
        if (signal == null) return "Belirsiz";
        return switch (signal) {
            case POSITIVE -> "Pozitif";
            case NEGATIVE -> "Negatif";
            case RISKY    -> "Riskli";
            case NEUTRAL  -> "NÃ¶tr";
        };
    }

    private String healthLabel(FinancialHealth health) {
        if (health == null) return "Belirsiz";
        return switch (health) {
            case STRONG -> "GÃ¼Ã§lÃ¼";
            case STABLE -> "Dengeli";
            case WATCH  -> "Ä°zleme";
            case RISKY  -> "Riskli";
        };
    }

    private List<String> nullSafe(List<String> list) {
        return list != null ? list : List.of();
    }
}




