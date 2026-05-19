package com.emrehalli.financeportal.news.service;

import com.emrehalli.financeportal.news.dto.response.NewsResponseDto;
import com.emrehalli.financeportal.news.entity.News;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NewsPresentationMapperTest {

    private final NewsPresentationMapper mapper = new NewsPresentationMapper();

    @Test
    void mapsKapDisclosureWithDedicatedQualityAndNoImage() {
        News news = News.builder()
                .id(11L)
                .externalId("KAP-1")
                .title("THYAO Ozel Durum Aciklamasi")
                .summary("Yonetim kurulu karari")
                .source("KAP")
                .provider("KAP")
                .language("tr")
                .regionScope("TR")
                .category("DISCLOSURE")
                .relatedSymbol("THYAO")
                .url("https://www.kap.org.tr/tr/Bildirim/1")
                .imageUrl("https://cdn.test/ignored.png")
                .publishedAt(LocalDateTime.of(2026, 5, 19, 10, 0))
                .importanceScore(82)
                .build();

        NewsResponseDto dto = mapper.toResponse(news);

        assertThat(dto.getIsKapDisclosure()).isTrue();
        assertThat(dto.getQualityStatus()).isEqualTo("KAP_DISCLOSURE");
        assertThat(dto.getImageUrl()).isNull();
        assertThat(dto.getDisclosureType()).isEqualTo("GENERAL");
        assertThat(dto.getContentPreview()).contains("THYAO");
    }

    @Test
    void infersFullContentForLongAaSummary() {
        News news = News.builder()
                .id(12L)
                .externalId("AA-1")
                .title("TCMB karari")
                .summary(("## Baslik\n\n" + "Detay ".repeat(120)).trim())
                .source("Anadolu Ajansi")
                .provider("AA_RSS")
                .language("tr")
                .regionScope("TR")
                .category("ECONOMY")
                .url("https://www.aa.com.tr/tr/ekonomi/test")
                .publishedAt(LocalDateTime.of(2026, 5, 19, 11, 0))
                .importanceScore(75)
                .build();

        NewsResponseDto dto = mapper.toResponse(news);

        assertThat(dto.getQualityStatus()).isEqualTo("FULL_CONTENT");
        assertThat(dto.getContentPreview()).doesNotContain("##");
        assertThat(dto.getSourceName()).isEqualTo("Anadolu Ajansi");
    }

    @Test
    void fallsBackToSourceLinkOnlyWhenSummaryMissing() {
        News news = News.builder()
                .id(13L)
                .externalId("INV-1")
                .title("Petrol fiyatlari yukseldi")
                .source("Investing.com")
                .provider("INVESTING_RSS")
                .language("en")
                .regionScope("GLOBAL")
                .category("ECONOMY")
                .url("https://www.investing.com/news/test")
                .publishedAt(LocalDateTime.of(2026, 5, 19, 12, 0))
                .importanceScore(40)
                .build();

        NewsResponseDto dto = mapper.toResponse(news);

        assertThat(dto.getQualityStatus()).isEqualTo("SOURCE_LINK_ONLY");
        assertThat(dto.getContentPreview()).isEqualTo("Petrol fiyatlari yukseldi");
        assertThat(dto.getSourceUrl()).isEqualTo(news.getUrl());
    }
}
