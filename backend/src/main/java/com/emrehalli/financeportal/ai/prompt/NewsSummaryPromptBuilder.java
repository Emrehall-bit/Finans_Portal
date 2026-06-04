package com.emrehalli.financeportal.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class NewsSummaryPromptBuilder implements AiPromptBuilder {

    private static final int MAX_NEWS_LENGTH = 3000;

    public String build(String symbol, String newsText, String language) {
        String trimmedNews = newsText != null && newsText.length() > MAX_NEWS_LENGTH
                ? newsText.substring(0, MAX_NEWS_LENGTH) : newsText;

        return AiPromptBuilder.languageInstruction(language) + """
                Sen Finance Portal'in uzman finansal haber analisti asistanisin.
                Asagidaki haber metnini okuyarak %s hissesiyle ilgili kisa ve nesnel bir ozet cikart.
                Finansal analist dilini kullan; abartili ifadelerden kacin.

                KURALLAR:
                - Haberin ana bulgusunu 1 cumlede ozetle.
                - Hisseye olasi etkisini (pozitif / notr / negatif) 1-2 cumleyle belirt.
                - Yatirim tavsiyesi verme; yalnizca haberin icerigini yorumla.
                - Haberde gecmeyen bilgileri ekleme.
                - Maksimum 4 cumle.

                HABER METNI:
                %s
                """.formatted(symbol, trimmedNews);
    }
}
