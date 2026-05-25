package com.emrehalli.financeportal.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class NewsSummaryPromptBuilder implements AiPromptBuilder {

    private static final int MAX_NEWS_LENGTH = 3000;

    public String build(String symbol, String newsText) {
        String trimmedNews = newsText != null && newsText.length() > MAX_NEWS_LENGTH
                ? newsText.substring(0, MAX_NEWS_LENGTH) : newsText;

        return """
                Sen Finance Portal'ın uzman finansal haber analisti asistanısın.
                Aşağıdaki haber metnini okuyarak %s hissesiyle ilgili kısa ve nesnel bir özet çıkar.
                Türkçe yaz. Finansal analist dilini kullan; abartılı ifadelerden kaçın.

                KURALLAR:
                - Haberin ana bulgusunu 1 cümlede özetle.
                - Hisseye olası etkisini (pozitif / nötr / negatif) 1-2 cümleyle belirt.
                - Yatırım tavsiyesi verme; yalnızca haberin içeriğini yorumla.
                - Haberde geçmeyen bilgileri ekleme.
                - Maksimum 4 cümle.

                HABER METNİ:
                %s
                """.formatted(symbol, trimmedNews);
    }
}



