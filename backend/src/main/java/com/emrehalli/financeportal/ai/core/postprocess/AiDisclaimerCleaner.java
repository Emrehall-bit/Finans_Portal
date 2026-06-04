package com.emrehalli.financeportal.ai.core.postprocess;

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
        String ascii = lower
                .replace('\u0131', 'i')
                .replace('\u011f', 'g')
                .replace('\u00fc', 'u')
                .replace('\u015f', 's')
                .replace('\u00f6', 'o')
                .replace('\u00e7', 'c');
        return (lower.contains("tavsiye") && (lower.contains("yat") || lower.contains("niteli") || lower.contains("bilgilendirme")))
                || lower.contains("yatÃ„Â±rÃ„Â±m tavsiyesi")
                || lower.contains("yat\u0131r\u0131m tavsiyesi")
                || ascii.contains("yatirim tavsiyesi")
                || lower.contains("tavsiye niteliÃ„Å¸inde deÃ„Å¸il")
                || lower.contains("tavsiye niteli\u011finde de\u011fil")
                || ascii.contains("tavsiye niteliginde degil")
                || lower.contains("bilgilendirme amaÃƒÂ§lÃ„Â±")
                || lower.contains("bilgilendirme ama\u00e7l\u0131")
                || ascii.contains("bilgilendirme amacli")
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
