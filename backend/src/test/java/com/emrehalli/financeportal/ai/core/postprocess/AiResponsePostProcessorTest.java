package com.emrehalli.financeportal.ai.core.postprocess;

import com.emrehalli.financeportal.ai.core.provider.AiTaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiResponsePostProcessorTest {

    private AiResponsePostProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AiResponsePostProcessor(
                new TurkishFinancialTextCleaner(),
                new AiDisclaimerCleaner()
        );
    }

    // â”€â”€ 1. null / blank safety â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void process_null_returnsEmpty() {
        assertThat(processor.process(null, AiTaskType.CHAT)).isEmpty();
    }

    @Test
    void process_blank_returnsEmpty() {
        assertThat(processor.process("   ", AiTaskType.CHAT)).isEmpty();
    }

    @Test
    void process_emptyString_returnsEmpty() {
        assertThat(processor.process("", AiTaskType.TECHNICAL_ANALYSIS)).isEmpty();
    }

    // â”€â”€ 2. Typo corrections â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void process_fixesVerbesirilmelidir() {
        String result = processor.process(
                "Performans verbesirilmelidir.", AiTaskType.CHAT);
        assertThat(result).contains("iyileÅŸtirilmelidir");
        assertThat(result).doesNotContain("verbesirilmeli");
    }

    @Test
    void process_fixesVerbesirilmeli() {
        String result = processor.process(
                "Oran verbesirilmeli.", AiTaskType.CHAT);
        assertThat(result).contains("iyileÅŸtirilmeli");
        assertThat(result).doesNotContain("verbesirilmeli");
    }

    // â”€â”€ 3. Disclaimer removal â€” TECHNICAL_ANALYSIS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void process_removesDisclaimerForTechnical() {
        String input = "THYAO olumlu gÃ¶rÃ¼nÃ¼yor. Bu yorum yatÄ±rÄ±m tavsiyesi deÄŸildir; yalnÄ±zca mevcut verilerin otomatik analizidir.";
        String result = processor.process(input, AiTaskType.TECHNICAL_ANALYSIS);
        assertThat(result).contains("THYAO olumlu gÃ¶rÃ¼nÃ¼yor");
        assertThat(result).doesNotContain("yatÄ±rÄ±m tavsiyesi");
    }

    // â”€â”€ 4. Disclaimer removal â€” FUNDAMENTAL_ANALYSIS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void process_removesDisclaimerForFundamental() {
        String input = "ROE yÃ¼ksek ve olumlu bir gÃ¶sterge. Bu yorum yatÄ±rÄ±m tavsiyesi deÄŸildir.";
        String result = processor.process(input, AiTaskType.FUNDAMENTAL_ANALYSIS);
        assertThat(result).contains("ROE yÃ¼ksek");
        assertThat(result).doesNotContain("yatÄ±rÄ±m tavsiyesi");
    }

    // â”€â”€ 5. Disclaimer kept (once) for CHAT â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void process_keepsOneDisclaimerForChat() {
        String input = "Hisse olumlu. Bu yorum yatÄ±rÄ±m tavsiyesi deÄŸildir. "
                + "GÃ¼zel gÃ¶rÃ¼nÃ¼yor. Bu yorum yatÄ±rÄ±m tavsiyesi deÄŸildir.";
        String result = processor.process(input, AiTaskType.CHAT);
        assertThat(countOccurrences(result, "yatÄ±rÄ±m tavsiyesi")).isEqualTo(1);
    }

    @Test
    void process_singleDisclaimerInChatIsKept() {
        String input = "Analiz olumlu. Bu yorum yatÄ±rÄ±m tavsiyesi deÄŸildir.";
        String result = processor.process(input, AiTaskType.CHAT);
        assertThat(result).contains("yatÄ±rÄ±m tavsiyesi");
    }

    // â”€â”€ 6. Duplicate sentence removal â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void process_removesDuplicateSentences() {
        String input = "RSI 70 Ã¼zerinde. RSI 70 Ã¼zerinde. AÅŸÄ±rÄ± alÄ±m bÃ¶lgesi.";
        String result = processor.process(input, AiTaskType.TECHNICAL_ANALYSIS);
        assertThat(countOccurrences(result, "RSI 70 Ã¼zerinde")).isEqualTo(1);
    }

    @Test
    void process_keepsNonDuplicateSentences() {
        String input = "Trend yukarÄ± yÃ¶nlÃ¼. RSI nÃ¶tr bÃ¶lgede.";
        String result = processor.process(input, AiTaskType.TECHNICAL_ANALYSIS);
        assertThat(result).contains("Trend yukarÄ± yÃ¶nlÃ¼");
        assertThat(result).contains("RSI nÃ¶tr bÃ¶lgede");
    }

    // â”€â”€ 7. Long text cut at sentence boundary â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void process_truncatesLongTextAtSentenceBoundary() {
        // Sentences must be unique so deduplication step doesn't collapse them before truncation
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sb.append("Bu bir teknik analiz cÃ¼mlesidir ve fiyat hareketlerini gÃ¶sterir, sÄ±ra ")
              .append(i + 1).append(". ");
        }
        sb.append("Bu cÃ¼mle asla gÃ¶rÃ¼nmemeli Ã§Ã¼nkÃ¼ limit aÅŸÄ±ldÄ±");
        String result = processor.process(sb.toString(), AiTaskType.TECHNICAL_ANALYSIS);
        assertThat(result.length()).isLessThanOrEqualTo(TurkishFinancialTextCleaner.MAX_LENGTH);
        assertThat(result).doesNotContain("Bu cÃ¼mle asla gÃ¶rÃ¼nmemeli");
    }

    @Test
    void process_shortTextIsNotTruncated() {
        String input = "THYAO teknik gÃ¶rÃ¼nÃ¼mÃ¼ olumlu. RSI dengeli.";
        String result = processor.process(input, AiTaskType.TECHNICAL_ANALYSIS);
        assertThat(result).isEqualTo("THYAO teknik gÃ¶rÃ¼nÃ¼mÃ¼ olumlu. RSI dengeli.");
    }

    // â”€â”€ 8. Robotic phrase â€” izlenmelidir â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void process_replacesIzlenmelidir() {
        String input = "Fiyat geliÅŸmeleri izlenmelidir.";
        String result = processor.process(input, AiTaskType.CHAT);
        assertThat(result).doesNotContain("izlenmelidir");
        assertThat(result).contains("takip edilmeli");
    }

    // â”€â”€ 9. Robotic phrase â€” deÄŸerlendirilebilir (frequency-aware) â”€

    @Test
    void process_reducesRepeatedDegerlendirileBilir() {
        String input = "Durum deÄŸerlendirilebilir. Performans da deÄŸerlendirilebilir.";
        String result = processor.process(input, AiTaskType.CHAT);
        assertThat(countOccurrences(result, "deÄŸerlendirilebilir")).isLessThan(2);
    }

    @Test
    void process_firstDegerlendirileBilirIsKept() {
        String input = "Durum deÄŸerlendirilebilir. Performans da deÄŸerlendirilebilir.";
        String result = processor.process(input, AiTaskType.CHAT);
        assertThat(result).contains("deÄŸerlendirilebilir");
    }

    // â”€â”€ 10. "mevcut verilere gÃ¶re" frequency cap â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void process_reducesMevcutVeriWhenTooFrequent() {
        String repeated = "Mevcut verilere gÃ¶re trend iyi. "
                + "Mevcut verilere gÃ¶re RSI nÃ¶tr. "
                + "Mevcut verilere gÃ¶re momentum zayÄ±f.";
        String result = processor.process(repeated, AiTaskType.CHAT);
        assertThat(countOccurrences(result.toLowerCase(java.util.Locale.ROOT),
                "mevcut verilere gÃ¶re")).isLessThanOrEqualTo(2);
    }

    // â”€â”€ 11. Whitespace normalization â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void process_normalizesMultipleSpaces() {
        String input = "Hisse  iyi  gÃ¶rÃ¼nÃ¼yor.";
        String result = processor.process(input, AiTaskType.CHAT);
        assertThat(result).doesNotContain("  ");
    }

    @Test
    void process_reducesExcessiveNewlines() {
        String input = "Trend iyi.\n\n\n\nRSI nÃ¶tr.";
        String result = processor.process(input, AiTaskType.CHAT);
        assertThat(result).doesNotContain("\n\n\n");
    }

    // â”€â”€ Helper â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private int countOccurrences(String text, String phrase) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(phrase, idx)) >= 0) {
            count++;
            idx += phrase.length();
        }
        return count;
    }
}




