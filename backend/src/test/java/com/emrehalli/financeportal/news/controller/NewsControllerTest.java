package com.emrehalli.financeportal.news.controller;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.news.dto.response.NewsRelatedResponseDto;
import com.emrehalli.financeportal.news.dto.response.RelatedNewsItemDto;
import com.emrehalli.financeportal.news.service.NewsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NewsControllerTest {

    @Mock
    private NewsService newsService;

    @Mock
    private AppMessageSource appMessageSource;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        NewsController newsController = new NewsController(newsService, appMessageSource);
        mockMvc = MockMvcBuilders.standaloneSetup(newsController).build();
    }

    @Test
    void relatedEndpointReturns200WithEmptyLists() throws Exception {
        when(newsService.getRelatedData(42L))
                .thenReturn(NewsRelatedResponseDto.builder()
                        .relatedNews(List.of())
                        .build());
        when(appMessageSource.get("news.detail.fetched")).thenReturn("News detail fetched");

        mockMvc.perform(get("/api/v1/news/42/related")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.relatedNews").isArray())
                .andExpect(jsonPath("$.data.relatedNews").isEmpty());
    }

    @Test
    void relatedEndpointReturns200WithPayload() throws Exception {
        when(newsService.getRelatedData(7L))
                .thenReturn(NewsRelatedResponseDto.builder()
                        .relatedNews(List.of(RelatedNewsItemDto.builder()
                                .id(9L)
                                .title("TCMB faiz karari sonrasi kur hareketlendi")
                                .category("INTEREST_BONDS")
                                .publishedAt(LocalDateTime.parse("2026-05-23T10:00:00"))
                                .sourceName("AA")
                                .build()))
                        .build());
        when(appMessageSource.get("news.detail.fetched")).thenReturn("News detail fetched");

        mockMvc.perform(get("/api/v1/news/7/related")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.relatedNews[0].id").value(9L));
    }
}




