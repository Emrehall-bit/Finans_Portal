package com.emrehalli.financeportal.ai.postprocess;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manages disclaimer sentences in AI-generated text.
 * Splits text by sentence boundaries and filters disclaimer sentences
 * according to the task type's policy.
 */
@Component
public class AiDisclaimerCleaner {

    /**
     * Removes all disclaimer sentences from the text body.
     * Used when the frontend card already shows a static disclaimer.
     */
    public String removeFromBody(String text) {
        List<String> sentences = splitSentences(text);
        List<String> kept = new ArrayList<>();
        for (String s : sentences) {
            if (!isDisclaimer(s)) {
                kept.add(s);
            }
        }
        return joinSentences(kept);
    }

    /**
     * Keeps at most one disclaimer in chat responses; drops any extras.
     */
    public String deduplicateInChat(String text) {
        List<String> sentences = splitSentences(text);
        List<String> result = new ArrayList<>();
        boolean kept = false;
        for (String s : sentences) {
            if (isDisclaimer(s)) {
                if (!kept) {
                    result.add(s);
                    kept = true;
                }
            } else {
                result.add(s);
            }
        }
        return joinSentences(result);
    }

    boolean isDisclaimer(String sentence) {
        String lower = sentence.toLowerCase(Locale.ROOT);
        return lower.contains("yatÄ±rÄ±m tavsiyesi")
                || lower.contains("tavsiye niteliÄŸinde deÄŸil")
                || lower.contains("bilgilendirme amaÃ§lÄ±")
                || lower.contains("otomatik analiz");
    }

    private List<String> splitSentences(String text) {
        String[] parts = text.split("(?<=[.!?])(?=\\s|$)");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String joinSentences(List<String> sentences) {
        return String.join(" ", sentences).trim();
    }
}




