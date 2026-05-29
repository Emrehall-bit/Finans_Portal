package com.emrehalli.financeportal.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class ChatPromptBuilder implements AiPromptBuilder {

    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_CONTEXT_LENGTH = 3000;

    public String build(String message, String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Sen Finance Portal'Ä±n uzman finansal asistanÄ±sÄ±n. ");
        sb.append("TÃ¼rk borsasÄ± (BIST), kripto, dÃ¶viz, ÅŸirket finansallarÄ± ve piyasalar hakkÄ±nda ");
        sb.append("doÄŸrudan ve profesyonel yorum yaparsÄ±n.\n\n");
        sb.append("DAVRANIÅ KURALLARI:\n");
        sb.append("- \"BakÄ±labilir\", \"incelenebilir\", \"analiz edilmeli\" gibi muÄŸlak ifade kullanma; direkt yorum yap.\n");
        sb.append("- Oran listeleme deÄŸil, yorumlama yap. \"ROE 18%\" deÄŸil, \"ÅŸirket Ã¶zkaynaklarÄ±nÄ± verimli kullanÄ±yor\" yaz.\n");
        sb.append("- YatÄ±rÄ±m tavsiyesi ve 'al'/'sat'/'tut' yÃ¶nlendirmesi yapma.\n");
        sb.append("- VeritabanÄ±nda olmayan spesifik sayÄ±larÄ± uydurma; \"bu veriye ÅŸu an eriÅŸimim yok\" de.\n");
        sb.append("- \"Mevcut veriler Ä±ÅŸÄ±ÄŸÄ±nda\", \"gÃ¶rÃ¼nÃ¼yor\" gibi kontrollÃ¼ dil kullan.\n");
        sb.append("- Maksimum 3-4 cÃ¼mle veya 4-6 madde. \"YatÄ±rÄ±m tavsiyesi deÄŸildir\" notu ekleme.\n\n");

        if (context != null && !context.isBlank()) {
            String trimmedCtx = context.length() > MAX_CONTEXT_LENGTH
                    ? context.substring(0, MAX_CONTEXT_LENGTH) : context;
            sb.append("BaÄŸlam (kullanÄ±cÄ±nÄ±n incelediÄŸi ekran): ").append(trimmedCtx).append("\n\n");
        }

        String trimmedMsg = message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
        sb.append("KullanÄ±cÄ± sorusu: ").append(trimmedMsg).append("\n\n");
        sb.append("YanÄ±tÄ±nÄ± yalnÄ±zca ÅŸu JSON formatÄ±nda ver: {\"reply\": \"cevabÄ±n\"}");

        return sb.toString();
    }
}




