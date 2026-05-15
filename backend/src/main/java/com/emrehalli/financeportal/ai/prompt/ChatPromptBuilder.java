package com.emrehalli.financeportal.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class ChatPromptBuilder implements AiPromptBuilder {

    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_CONTEXT_LENGTH = 3000;

    public String build(String message, String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Sen Finance Portal'ın uzman finansal asistanısın. ");
        sb.append("Türk borsası (BIST), kripto, döviz, şirket finansalları ve piyasalar hakkında ");
        sb.append("doğrudan ve profesyonel yorum yaparsın.\n\n");
        sb.append("DAVRANIŞ KURALLARI:\n");
        sb.append("- \"Bakılabilir\", \"incelenebilir\", \"analiz edilmeli\" gibi muğlak ifade kullanma; direkt yorum yap.\n");
        sb.append("- Oran listeleme değil, yorumlama yap. \"ROE 18%\" değil, \"şirket özkaynaklarını verimli kullanıyor\" yaz.\n");
        sb.append("- Yatırım tavsiyesi ve 'al'/'sat'/'tut' yönlendirmesi yapma.\n");
        sb.append("- Veritabanında olmayan spesifik sayıları uydurma; \"bu veriye şu an erişimim yok\" de.\n");
        sb.append("- \"Mevcut veriler ışığında\", \"görünüyor\" gibi kontrollü dil kullan.\n");
        sb.append("- Maksimum 3-4 cümle veya 4-6 madde. \"Yatırım tavsiyesi değildir\" notu ekleme.\n\n");

        if (context != null && !context.isBlank()) {
            String trimmedCtx = context.length() > MAX_CONTEXT_LENGTH
                    ? context.substring(0, MAX_CONTEXT_LENGTH) : context;
            sb.append("Bağlam (kullanıcının incelediği ekran): ").append(trimmedCtx).append("\n\n");
        }

        String trimmedMsg = message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
        sb.append("Kullanıcı sorusu: ").append(trimmedMsg).append("\n\n");
        sb.append("Yanıtını yalnızca şu JSON formatında ver: {\"reply\": \"cevabın\"}");

        return sb.toString();
    }
}
