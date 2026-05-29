package com.emrehalli.financeportal.ai.postprocess;

import com.emrehalli.financeportal.ai.provider.AiTaskType;
import org.springframework.stereotype.Component;

/**
 * Pipeline that cleans AI-generated text without making additional LLM calls.
 *
 * Steps (in order):
 *   1. null / blank guard
 *   2. whitespace normalization
 *   3. typo corrections
 *   4. disclaimer policy (remove for analytical tasks, deduplicate for chat)
 *   5. robotic phrase reduction
 *   6. duplicate sentence removal + "mevcut verilere gÃ¶re" frequency cap
 *   7. sentence-boundary-aware truncation at 1 500 chars
 */
@Component
public class AiResponsePostProcessor {

    private final TurkishFinancialTextCleaner textCleaner;
    private final AiDisclaimerCleaner disclaimerCleaner;

    public AiResponsePostProcessor(TurkishFinancialTextCleaner textCleaner,
                                   AiDisclaimerCleaner disclaimerCleaner) {
        this.textCleaner = textCleaner;
        this.disclaimerCleaner = disclaimerCleaner;
    }

    public String process(String text, AiTaskType taskType) {
        if (text == null) return "";
        if (text.isBlank()) return text.trim();

        String result = text;
        result = textCleaner.normalizeWhitespace(result);
        result = textCleaner.fixTypos(result);
        result = applyDisclaimerPolicy(result, taskType);
        result = textCleaner.reduceRoboticPhrases(result);
        result = textCleaner.deduplicateSentences(result);
        result = textCleaner.truncateAtSentenceBoundary(result);
        return result.trim();
    }

    private String applyDisclaimerPolicy(String text, AiTaskType taskType) {
        return switch (taskType) {
            case CHAT -> disclaimerCleaner.deduplicateInChat(text);
            case TECHNICAL_ANALYSIS, FUNDAMENTAL_ANALYSIS, PAGE_ANALYSIS, COMPANY_COMPARISON, PORTFOLIO_ANALYSIS, NEWS_SUMMARY, NEWS_IMPACT_ANALYSIS ->
                    disclaimerCleaner.removeFromBody(text);
        };
    }
}




