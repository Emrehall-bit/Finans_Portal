package com.emrehalli.financeportal.ai.postprocess;

import com.emrehalli.financeportal.ai.provider.AiTaskType;
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

    // ── 1. null / blank safety ────────────────────────────────────

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

    // ── 2. Typo corrections ───────────────────────────────────────

    @Test
    void process_fixesVerbesirilmelidir() {
        String result = processor.process(
                "Performans verbesirilmelidir.", AiTaskType.CHAT);
        assertThat(result).contains("iyileştirilmelidir");
        assertThat(result).doesNotContain("verbesirilmeli");
    }

    @Test
    void process_fixesVerbesirilmeli() {
        String result = processor.process(
                "Oran verbesirilmeli.", AiTaskType.CHAT);
        assertThat(result).contains("iyileştirilmeli");
        assertThat(result).doesNotContain("verbesirilmeli");
    }

    // ── 3. Disclaimer removal — TECHNICAL_ANALYSIS ───────────────

    @Test
    void process_removesDisclaimerForTechnical() {
        String input = "THYAO olumlu görünüyor. Bu yorum yatırım tavsiyesi değildir; yalnızca mevcut verilerin otomatik analizidir.";
        String result = processor.process(input, AiTaskType.TECHNICAL_ANALYSIS);
        assertThat(result).contains("THYAO olumlu görünüyor");
        assertThat(result).doesNotContain("yatırım tavsiyesi");
    }

    // ── 4. Disclaimer removal — FUNDAMENTAL_ANALYSIS ────────────

    @Test
    void process_removesDisclaimerForFundamental() {
        String input = "ROE yüksek ve olumlu bir gösterge. Bu yorum yatırım tavsiyesi değildir.";
        String result = processor.process(input, AiTaskType.FUNDAMENTAL_ANALYSIS);
        assertThat(result).contains("ROE yüksek");
        assertThat(result).doesNotContain("yatırım tavsiyesi");
    }

    // ── 5. Disclaimer kept (once) for CHAT ───────────────────────

    @Test
    void process_keepsOneDisclaimerForChat() {
        String input = "Hisse olumlu. Bu yorum yatırım tavsiyesi değildir. "
                + "Güzel görünüyor. Bu yorum yatırım tavsiyesi değildir.";
        String result = processor.process(input, AiTaskType.CHAT);
        assertThat(countOccurrences(result, "yatırım tavsiyesi")).isEqualTo(1);
    }

    @Test
    void process_singleDisclaimerInChatIsKept() {
        String input = "Analiz olumlu. Bu yorum yatırım tavsiyesi değildir.";
        String result = processor.process(input, AiTaskType.CHAT);
        assertThat(result).contains("yatırım tavsiyesi");
    }

    // ── 6. Duplicate sentence removal ────────────────────────────

    @Test
    void process_removesDuplicateSentences() {
        String input = "RSI 70 üzerinde. RSI 70 üzerinde. Aşırı alım bölgesi.";
        String result = processor.process(input, AiTaskType.TECHNICAL_ANALYSIS);
        assertThat(countOccurrences(result, "RSI 70 üzerinde")).isEqualTo(1);
    }

    @Test
    void process_keepsNonDuplicateSentences() {
        String input = "Trend yukarı yönlü. RSI nötr bölgede.";
        String result = processor.process(input, AiTaskType.TECHNICAL_ANALYSIS);
        assertThat(result).contains("Trend yukarı yönlü");
        assertThat(result).contains("RSI nötr bölgede");
    }

    // ── 7. Long text cut at sentence boundary ────────────────────

    @Test
    void process_truncatesLongTextAtSentenceBoundary() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sb.append("Bu bir teknik analiz cümlesidir ve fiyat hareketlerini gösterir. ");
        }
        sb.append("Bu cümle asla görünmemeli çünkü limit aşıldı");
        String result = processor.process(sb.toString(), AiTaskType.TECHNICAL_ANALYSIS);
        assertThat(result.length()).isLessThanOrEqualTo(TurkishFinancialTextCleaner.MAX_LENGTH);
        assertThat(result).doesNotContain("Bu cümle asla görünmemeli");
    }

    @Test
    void process_shortTextIsNotTruncated() {
        String input = "THYAO teknik görünümü olumlu. RSI dengeli.";
        String result = processor.process(input, AiTaskType.TECHNICAL_ANALYSIS);
        assertThat(result).isEqualTo("THYAO teknik görünümü olumlu. RSI dengeli.");
    }

    // ── 8. Robotic phrase — izlenmelidir ─────────────────────────

    @Test
    void process_replacesIzlenmelidir() {
        String input = "Fiyat gelişmeleri izlenmelidir.";
        String result = processor.process(input, AiTaskType.CHAT);
        assertThat(result).doesNotContain("izlenmelidir");
        assertThat(result).contains("takip edilmeli");
    }

    // ── 9. Robotic phrase — değerlendirilebilir (frequency-aware) ─

    @Test
    void process_reducesRepeatedDegerlendirileBilir() {
        String input = "Durum değerlendirilebilir. Performans da değerlendirilebilir.";
        String result = processor.process(input, AiTaskType.CHAT);
        assertThat(countOccurrences(result, "değerlendirilebilir")).isLessThan(2);
    }

    @Test
    void process_firstDegerlendirileBilirIsKept() {
        String input = "Durum değerlendirilebilir. Performans da değerlendirilebilir.";
        String result = processor.process(input, AiTaskType.CHAT);
        assertThat(result).contains("değerlendirilebilir");
    }

    // ── 10. "mevcut verilere göre" frequency cap ─────────────────

    @Test
    void process_reducesMevcutVeriWhenTooFrequent() {
        String repeated = "Mevcut verilere göre trend iyi. "
                + "Mevcut verilere göre RSI nötr. "
                + "Mevcut verilere göre momentum zayıf.";
        String result = processor.process(repeated, AiTaskType.CHAT);
        assertThat(countOccurrences(result.toLowerCase(java.util.Locale.ROOT),
                "mevcut verilere göre")).isLessThanOrEqualTo(2);
    }

    // ── 11. Whitespace normalization ──────────────────────────────

    @Test
    void process_normalizesMultipleSpaces() {
        String input = "Hisse  iyi  görünüyor.";
        String result = processor.process(input, AiTaskType.CHAT);
        assertThat(result).doesNotContain("  ");
    }

    @Test
    void process_reducesExcessiveNewlines() {
        String input = "Trend iyi.\n\n\n\nRSI nötr.";
        String result = processor.process(input, AiTaskType.CHAT);
        assertThat(result).doesNotContain("\n\n\n");
    }

    // ── Helper ────────────────────────────────────────────────────

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
