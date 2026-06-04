package com.emrehalli.financeportal.ai.prompt;

public interface AiPromptBuilder {

    /**
     * Returns a language instruction line to prepend to every prompt.
     * Normalises the tag so "en", "en-US", "en-GB" all map to English;
     * everything else (including null/blank) defaults to Turkish.
     */
    static String languageInstruction(String language) {
        if (language != null && language.trim().toLowerCase().startsWith("en")) {
            return "Language: Respond in clear, professional English. " +
                   "Do not produce Turkish text. " +
                   "Do not give direct investment advice (buy/sell/hold).\n\n";
        }
        return "Dil: Akici, profesyonel Turkce yaz. " +
               "Gereksiz Ingilizce terim kullanma; Turkce karsiliklerini tercih et. " +
               "'al/sat/tut' gibi yatirim tavsiyesi ifadelerinden kacin.\n\n";
    }

    /** Normalises a raw language tag to a safe two-letter code (tr or en). */
    static String normLang(String language) {
        if (language != null && language.trim().toLowerCase().startsWith("en")) return "en";
        return "tr";
    }
}
