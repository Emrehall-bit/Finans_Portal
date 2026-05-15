package com.emrehalli.financeportal.ai.postprocess;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-text cleanup utilities for Turkish financial AI output.
 * No LLM calls — only string/regex operations.
 */
@Component
public class TurkishFinancialTextCleaner {

    static final int MAX_LENGTH = 1500;

    private static final Pattern MEVCUT_VERI_PATTERN = Pattern.compile(
            "mevcut verilere göre[,]?\\s*", Pattern.CASE_INSENSITIVE
    );

    // ── Public pipeline steps ─────────────────────────────────────

    public String normalizeWhitespace(String text) {
        return text
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    public String fixTypos(String text) {
        // Longer string checked first to avoid partial replacements
        return text
                .replace("verbesirilmelidir", "iyileştirilmelidir")
                .replace("verbesirilmeli", "iyileştirilmeli");
    }

    public String reduceRoboticPhrases(String text) {
        String result = text;

        // Always-replace: these phrases are robotic in any context
        result = result.replace(
                "toparlanma işaretlerinin beklenmesi",
                "toparlanma için fiyatın yeniden güç kazanması"
        );
        // Sentence-ending variant first so the period stays
        result = result.replace("izlenmelidir.", "takip edilmeli.");
        result = result.replace("izlenmelidir", "takip edilmeli");

        // Frequency-aware: keep first occurrence, replace the rest
        result = reduceRepeated(result, "değerlendirilebilir",
                new String[]{"dikkat çekiyor", "öne çıkıyor"});
        result = reduceRepeated(result, "incelenebilir",
                new String[]{"takip edilebilir", "öne çıkıyor"});

        return result;
    }

    public String deduplicateSentences(String text) {
        String[] parts = text.split("(?<=[.!?])(?=\\s)");
        Set<String> seen = new LinkedHashSet<>();
        List<String> unique = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            if (seen.add(trimmed.toLowerCase(Locale.ROOT))) {
                unique.add(trimmed);
            }
        }
        String joined = String.join(" ", unique);
        return reduceMevcutVeri(joined);
    }

    public String truncateAtSentenceBoundary(String text) {
        if (text.length() <= MAX_LENGTH) return text;
        String sub = text.substring(0, MAX_LENGTH);
        for (int i = sub.length() - 1; i >= MAX_LENGTH / 2; i--) {
            char c = sub.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                return sub.substring(0, i + 1).trim();
            }
        }
        int lastSpace = sub.lastIndexOf(' ');
        return (lastSpace > 0 ? sub.substring(0, lastSpace) : sub).trim();
    }

    // ── Package-private helpers (used in tests) ───────────────────

    String reduceMevcutVeri(String text) {
        return reducePatternInstances(text, MEVCUT_VERI_PATTERN, 2);
    }

    /**
     * Finds all occurrences of {@code phrase} (case-insensitive by lowering both strings)
     * and replaces the 2nd and subsequent occurrences with rotating {@code alternatives}.
     * The first occurrence is kept verbatim so its original casing is preserved.
     */
    String reduceRepeated(String text, String phrase, String[] alternatives) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerPhrase = phrase.toLowerCase(Locale.ROOT);
        List<Integer> positions = new ArrayList<>();
        int pos = 0;
        while ((pos = lowerText.indexOf(lowerPhrase, pos)) >= 0) {
            positions.add(pos);
            pos += lowerPhrase.length();
        }
        if (positions.size() <= 1) return text;

        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        int altIdx = 0;
        for (int i = 0; i < positions.size(); i++) {
            int start = positions.get(i);
            sb.append(text, lastEnd, start);
            if (i == 0) {
                sb.append(text, start, start + phrase.length()); // keep original
            } else {
                sb.append(alternatives[altIdx % alternatives.length]);
                altIdx++;
            }
            lastEnd = start + phrase.length();
        }
        sb.append(text.substring(lastEnd));
        return sb.toString();
    }

    private String reducePatternInstances(String text, Pattern p, int maxKeep) {
        Matcher m = p.matcher(text);
        int total = 0;
        while (m.find()) total++;
        if (total <= maxKeep) return text;

        m.reset();
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        int lastEnd = 0;
        while (m.find()) {
            if (kept < maxKeep) {
                sb.append(text, lastEnd, m.end());
                kept++;
            } else {
                sb.append(text, lastEnd, m.start()); // drop match, keep surrounding text
            }
            lastEnd = m.end();
        }
        sb.append(text.substring(lastEnd));
        return sb.toString();
    }
}
