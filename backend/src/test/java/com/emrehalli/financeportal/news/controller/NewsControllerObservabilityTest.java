package com.emrehalli.financeportal.news.controller;

import com.emrehalli.financeportal.common.exception.GlobalExceptionHandler;
import com.emrehalli.financeportal.config.ObservabilityFilterConfig;
import com.emrehalli.financeportal.config.security.KeycloakJwtRoleConverter;
import com.emrehalli.financeportal.config.security.ResourceAccessManager;
import com.emrehalli.financeportal.config.security.SecurityConfig;
import com.emrehalli.financeportal.news.dto.request.NewsSearchRequest;
import com.emrehalli.financeportal.news.dto.response.NewsResponseDto;
import com.emrehalli.financeportal.news.service.NewsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NewsController.class)
@Import({SecurityConfig.class, KeycloakJwtRoleConverter.class, ObservabilityFilterConfig.class, GlobalExceptionHandler.class})
class NewsControllerObservabilityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NewsService newsService;

    @MockBean
    private ResourceAccessManager resourceAccessManager;

    @Test
    void getNewsReturnsRequestIdHeader() throws Exception {
        when(newsService.getNews(any(NewsSearchRequest.class), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(new PageImpl<>(List.of(
                        NewsResponseDto.builder()
                                .id(1L)
                                .externalId("news-1")
                                .title("Market update")
                                .source("AA")
                                .provider("AA")
                                .language("tr")
                                .regionScope("TR")
                                .category("ECONOMY")
                                .url("https://example.com/news-1")
                                .publishedAt(LocalDateTime.parse("2026-04-25T10:15:30"))
                                .importanceScore(42)
                                .build()
                )));

        mockMvc.perform(get("/api/v1/news")
                        .header("X-Request-Id", "news-request-123")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "news-request-123"))
                .andExpect(jsonPath("$.data.content[0].title").value("Market update"));
    }
}
