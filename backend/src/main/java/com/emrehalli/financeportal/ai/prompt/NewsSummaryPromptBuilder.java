package com.emrehalli.financeportal.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class NewsSummaryPromptBuilder implements AiPromptBuilder {

    private static final int MAX_NEWS_LENGTH = 3000;

    public String build(String symbol, String newsText) {
        String trimmedNews = newsText != null && newsText.length() > MAX_NEWS_LENGTH
                ? newsText.substring(0, MAX_NEWS_LENGTH) : newsText;

        return """
                Sen Finance Portal'Ä±n uzman finansal haber analisti asistanÄ±sÄ±n.
                AÅŸaÄŸÄ±daki haber metnini okuyarak %s hissesiyle ilgili kÄ±sa ve nesnel bir Ã¶zet Ã§Ä±kar.
                TÃ¼rkÃ§e yaz. Finansal analist dilini kullan; abartÄ±lÄ± ifadelerden kaÃ§Ä±n.

                KURALLAR:
                - Haberin ana bulgusunu 1 cÃ¼mlede Ã¶zetle.
                - Hisseye olasÄ± etkisini (pozitif / nÃ¶tr / negatif) 1-2 cÃ¼mleyle belirt.
                - YatÄ±rÄ±m tavsiyesi verme; yalnÄ±zca haberin iÃ§eriÄŸini yorumla.
                - Haberde geÃ§meyen bilgileri ekleme.
                - Maksimum 4 cÃ¼mle.

                HABER METNÄ°:
                %s
                """.formatted(symbol, trimmedNews);
    }
}




