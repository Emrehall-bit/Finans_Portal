package com.emrehalli.financeportal.ai.core.prompt;

public interface AiPromptBuilder {

    static String languageInstruction(String language) {
        if (language != null && language.trim().toLowerCase().startsWith("en")) {
            return "Language: Respond in clear, professional English. " +
                   "Do not produce Turkish text. " +
                   "Do not give direct investment advice (buy/sell/hold).\n\n";
        }
        return "Dil: Akıcı, profesyonel Türkçe yaz. " +
               "Türkçe karakterleri (ğ, ş, ı, ü, ö, ç, İ, Ğ, Ş, Ü, Ö, Ç) eksiksiz kullan; " +
               "asla ASCII karşılıklarıyla (g, s, i, u, o, c) ikame etme. " +
               "Gereksiz İngilizce terim kullanma; Türkçe karşılıklarını tercih et. " +
               "'al/sat/tut' gibi yatırım tavsiyesi ifadelerinden kaçın.\n\n";
    }

    static String normLang(String language) {
        if (language != null && language.trim().toLowerCase().startsWith("en")) return "en";
        return "tr";
    }
}
