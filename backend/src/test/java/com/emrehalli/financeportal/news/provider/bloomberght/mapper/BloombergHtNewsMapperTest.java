package com.emrehalli.financeportal.news.provider.bloomberght.mapper;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BloombergHtNewsMapperTest {

    private final BloombergHtNewsMapper mapper = new BloombergHtNewsMapper();

    @Test
    void leavesPublishedAtNullWhenDateCannotBeParsed() {
        Document document = Jsoup.parse("""
                <main>
                  <article class="news-card">
                    <a href="https://www.bloomberght.com/ekonomi/ornek-haber-12345">Anchor text long enough</a>
                    <h2>Piyasalarda dalgalanma suruyor ve yeni karar bekleniyor</h2>
                    <p>Ozet metni burada yer aliyor.</p>
                    <span class="date">gecersiz-tarih</span>
                  </article>
                </main>
                """, "https://www.bloomberght.com");

        List<NewsItemDto> items = mapper.map(document);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getTitle()).isEqualTo("Piyasalarda dalgalanma suruyor ve yeni karar bekleniyor");
            assertThat(item.getPublishedAt()).isNull();
        });
    }

    @Test
    void parsesTurkishMonthNameDateTime() {
        Document document = Jsoup.parse("""
                <main>
                  <article class="news-card">
                    <a href="https://www.bloomberght.com/ekonomi/ornek-haber-54321">Anchor text long enough</a>
                    <h2>Merkez bankasi piyasalar icin yeni adimlar acikladi</h2>
                    <p>Ozet metni burada yer aliyor.</p>
                    <span class="date">25 Nisan 2026 14:30</span>
                  </article>
                </main>
                """, "https://www.bloomberght.com");

        List<NewsItemDto> items = mapper.map(document);

        assertThat(items).singleElement().satisfies(item ->
                assertThat(item.getPublishedAt()).isEqualTo(LocalDateTime.of(2026, 4, 25, 14, 30))
        );
    }
}
