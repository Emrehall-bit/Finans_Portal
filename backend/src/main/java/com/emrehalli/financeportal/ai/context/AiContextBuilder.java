package com.emrehalli.financeportal.ai.context;

import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse;
import org.springframework.stereotype.Component;

@Component
public class AiContextBuilder {

    public String buildInstrumentBlock(InstrumentContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== EKRAN BAĞLAMI: ").append(ctx.symbol()).append(" ===\n");

        AiTechnicalAnalysisResponse tech = ctx.technicalSummary();
        if (tech != null) {
            sb.append("TEKNİK ANALİZ:\n");
            if (tech.summary() != null) {
                sb.append("  Özet: ").append(tech.summary()).append("\n");
            }
            if (tech.trendComment() != null) {
                sb.append("  Trend: ").append(tech.trendComment()).append("\n");
            }
            if (tech.momentumComment() != null) {
                sb.append("  Momentum: ").append(tech.momentumComment()).append("\n");
            }
            if (tech.riskLevel() != null) {
                sb.append("  Risk seviyesi: ").append(tech.riskLevel()).append("\n");
            }
            if (tech.signal() != null) {
                sb.append("  Teknik sinyal: ").append(tech.signal()).append("\n");
            }
        }

        AiFundamentalAnalysisResponse fund = ctx.fundamentalSummary();
        if (fund != null) {
            sb.append("TEMEL ANALİZ:\n");
            if (fund.summary() != null) {
                sb.append("  Özet: ").append(fund.summary()).append("\n");
            }
            if (fund.strengths() != null && !fund.strengths().isEmpty()) {
                sb.append("  Güçlü yönler: ").append(String.join(" | ", fund.strengths())).append("\n");
            }
            if (fund.weaknesses() != null && !fund.weaknesses().isEmpty()) {
                sb.append("  Zayıf yönler: ").append(String.join(" | ", fund.weaknesses())).append("\n");
            }
            if (fund.risks() != null && !fund.risks().isEmpty()) {
                sb.append("  Riskler: ").append(String.join(" | ", fund.risks())).append("\n");
            }
            if (fund.growthComment() != null) {
                sb.append("  Büyüme: ").append(fund.growthComment()).append("\n");
            }
            if (fund.financialHealth() != null) {
                sb.append("  Finansal sağlık: ").append(fund.financialHealth()).append("\n");
            }
        }

        return sb.toString().trim();
    }

    public String buildDashboardBlock(DashboardContext ctx) {
        return "=== EKRAN BAĞLAMI: DASHBOARD ===\n" + ctx.screenNote();
    }
}



