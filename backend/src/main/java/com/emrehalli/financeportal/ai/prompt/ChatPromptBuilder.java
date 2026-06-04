package com.emrehalli.financeportal.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class ChatPromptBuilder implements AiPromptBuilder {

    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_CONTEXT_LENGTH = 3000;

    public String build(String message, String context, String language) {
        StringBuilder sb = new StringBuilder();
        sb.append(AiPromptBuilder.languageInstruction(language));
        sb.append("Sen Finance Portal'in uzman finansal asistanisin. ");
        sb.append("BIST, kripto, doviz, sirket finansallari ve piyasalar hakkinda ");
        sb.append("dogrudan ve profesyonel yorum yapiyorsun.\n\n");
        sb.append("DAVRANIS KURALLARI:\n");
        sb.append("- \"Bakilabilir\", \"incelenebilir\", \"analiz edilmeli\" gibi mugak ifade kullanma; direkt yorum yap.\n");
        sb.append("- Oran listeleme degil, yorumlama yap. \"ROE 18%\" degil, \"sirket ozkaynaklarini verimli kullaniyor\" yaz.\n");
        sb.append("- Yatirim tavsiyesi ve 'al'/'sat'/'tut' yonlendirmesi yapma.\n");
        sb.append("- Veritabaninda olmayan spesifik sayilari uydurma; \"bu veriye su an erisimim yok\" de.\n");
        sb.append("- \"Mevcut veriler isiginda\", \"gorunuyor\" gibi kontrollu dil kullan.\n");
        sb.append("- Maksimum 3-4 cumle veya 4-6 madde. \"Yatirim tavsiyesi degildir\" notu ekleme.\n\n");

        if (context != null && !context.isBlank()) {
            String trimmedCtx = context.length() > MAX_CONTEXT_LENGTH
                    ? context.substring(0, MAX_CONTEXT_LENGTH) : context;
            sb.append("Baglan (kullanicinin inceledigi ekran): ").append(trimmedCtx).append("\n\n");
        }

        String trimmedMsg = message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
        sb.append("Kullanici sorusu: ").append(trimmedMsg).append("\n\n");
        sb.append("Yanitini yalnizca su JSON formatinda ver: {\"reply\": \"cevabin\"}");

        return sb.toString();
    }
}
