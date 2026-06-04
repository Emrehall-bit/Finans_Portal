package com.emrehalli.financeportal.ai.features.news;

import com.emrehalli.financeportal.ai.core.prompt.AiPromptBuilder;
import org.springframework.stereotype.Component;

@Component
public class NewsImpactPromptBuilder {

    public String build(NewsImpactContext ctx, String language) {
        StringBuilder sb = new StringBuilder();
        sb.append(AiPromptBuilder.languageInstruction(language));

        sb.append("You are a careful financial news context analyst.\n");
        sb.append("Assume the user has already read the article. Do not summarize it.\n");
        sb.append("Your main job is to answer: Why does this news matter for a finance portal user?\n");
        sb.append("Extract financial meaning, not the story. Focus on market relevance, transmission channels, and what could make the impact clearer.\n");
        sb.append("Do not make the card sound more certain than the news supports. Prefer clear, modest, evidence-based language.\n\n");

        sb.append("RULES:\n");
        sb.append("1. Do not repeat or summarize the news. The user already knows what happened; explain why it may matter financially.\n");
        sb.append("2. Do not give buy/sell/hold advice, price targets, or certain price direction.\n");
        sb.append("3. Do not invent facts, data, symbols, or policy decisions not present in the news.\n");
        sb.append("4. Classify the impact naturally inside financialContext or marketImplication as one of these: direct market catalyst, indirect sentiment signal, thematic background, or mostly informational.\n");
        sb.append("5. financialContext must be 1-2 sentences about why an investor should care. Do not restate the title, summary, or who said what.\n");
        sb.append("6. marketImplication must explain the transmission channel: risk appetite, rate expectations, liquidity, sector rotation, regulation, foreign trade, payment infrastructure, energy costs, or another article-supported channel.\n");
        sb.append("7. If the news is not a direct price catalyst, say that clearly and explain whether the channel is indirect, thematic, or limited.\n");
        sb.append("8. affectedAssets must be produced only from the article text. Do not infer affected assets from category alone.\n");
        sb.append("9. affectedAssets must contain only clean, short labels such as asset classes, sectors, indices, or symbols. No explanations inside the array.\n");
        sb.append("10. Return affectedAssets as [] if there is no article-supported link. Use broad labels such as 'General market' only when the article is genuinely about broad market sentiment.\n");
        sb.append("11. watchIndicators must contain at most 3 concrete indicators or follow-up flows. Avoid generic items such as 'global economic developments', 'investor appetite', or 'market trends'.\n");
        sb.append("12. highlights must be 2-3 market takeaways, not a news summary. If there are no strong takeaways, return an empty array.\n");
        sb.append("13. shortTermImpact and mediumTermImpact are optional. Return null unless there is a clear, meaningful market catalyst.\n");
        sb.append("14. Never write generic filler such as 'no clear short-term impact' or 'more developments are needed'. If impact is unclear, use uncertainty and watchIndicators instead.\n");
        sb.append("15. uncertainty must be specific to the article, for example whether commentary turns into real price action, volume confirmation, regulation, or company action.\n");
        sb.append("16. Mention Turkey/BIST, FX, crypto, funds, banks, commodities, or sectors only when the news text supports a direct or indirect channel.\n");
        sb.append("17. If the response language is Turkish, use correct Turkish characters and never output mojibake/encoding-broken text.\n\n");

        sb.append("NEWS INPUT:\n");
        sb.append("Title: ").append(ctx.title()).append("\n");
        if (hasText(ctx.newsSummary())) {
            sb.append("Content: ").append(ctx.newsSummary()).append("\n");
        }
        sb.append("Detected category: ").append(ctx.detectedCategory().name()).append("\n");
        sb.append("Category evidence: titleMatched=").append(ctx.titleMatched())
                .append(", contentMatched=").append(ctx.summaryMatched())
                .append(", categoryOnly=").append(ctx.categoryOnly()).append("\n");
        sb.append("Source: ").append(ctx.source()).append("\n");
        if (hasText(ctx.relatedSymbol())) {
            sb.append("Related symbol: ").append(ctx.relatedSymbol()).append("\n");
        }

        sb.append("\nReturn only this JSON object. Use null for optional impacts when they are not genuinely meaningful.\n");
        sb.append("{\n");
        sb.append("  \"financialContext\": \"1-2 sentences explaining why this matters financially; no summary.\",\n");
        sb.append("  \"affectedAssets\": [\"AI-generated clean asset class, sector, index, or symbol supported by article text\"],\n");
        sb.append("  \"marketImplication\": \"Market implication with the transmission channel and whether the effect is direct, indirect, thematic, or limited.\",\n");
        sb.append("  \"watchIndicators\": [\"up to 3 concrete indicators or follow-up news flows\"],\n");
        sb.append("  \"uncertainty\": \"Specific uncertainty limiting the analysis.\",\n");
        sb.append("  \"highlights\": [\"2-3 market takeaways, not summary bullets\"],\n");
        sb.append("  \"shortTermImpact\": null,\n");
        sb.append("  \"mediumTermImpact\": null\n");
        sb.append("}\n");

        return sb.toString();
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
