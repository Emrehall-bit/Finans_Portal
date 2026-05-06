package com.emrehalli.financeportal.news.provider.finnhub;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.provider.finnhub.dto.FinnhubNewsResponse;
import com.emrehalli.financeportal.news.provider.finnhub.mapper.FinnhubNewsMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FinnhubNewsMapperTest {

    private final FinnhubNewsMapper mapper = new FinnhubNewsMapper();

    @Test
    void mapsImageFieldIntoImageUrl() {
        FinnhubNewsResponse response = new FinnhubNewsResponse();
        response.setId(101L);
        response.setHeadline("Fed decision in focus");
        response.setSummary("Markets await the latest update.");
        response.setSource("Finnhub");
        response.setCategory("general");
        response.setRelated("AAPL");
        response.setUrl("https://example.com/fed");
        response.setImage("https://cdn.example.com/fed.jpg");
        response.setDatetime(1_714_042_000L);

        NewsItemDto item = mapper.map(response);

        assertThat(item).isNotNull();
        assertThat(item.getImageUrl()).isEqualTo("https://cdn.example.com/fed.jpg");
    }

    @Test
    void ignoresProviderLogoStyleImages() {
        FinnhubNewsResponse response = new FinnhubNewsResponse();
        response.setId(102L);
        response.setHeadline("Reuters sourced story");
        response.setUrl("https://example.com/reuters");
        response.setImage("https://static2.finnhub.io/file/finnhub/logo/reuters_logo.jpeg");

        NewsItemDto item = mapper.map(response);

        assertThat(item).isNotNull();
        assertThat(item.getImageUrl()).isNull();
    }
}
