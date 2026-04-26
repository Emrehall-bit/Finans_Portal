package com.emrehalli.financeportal.news.controller;

import com.emrehalli.financeportal.config.security.KeycloakJwtRoleConverter;
import com.emrehalli.financeportal.config.security.ResourceAccessManager;
import com.emrehalli.financeportal.config.security.SecurityConfig;
import com.emrehalli.financeportal.news.dto.response.NewsImportanceRecalculationResponseDto;
import com.emrehalli.financeportal.news.service.NewsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NewsController.class)
@Import({SecurityConfig.class, KeycloakJwtRoleConverter.class})
class NewsAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NewsService newsService;

    @MockBean
    private ResourceAccessManager resourceAccessManager;

    @Test
    void adminCanRecalculateImportanceScores() throws Exception {
        when(newsService.recalculateImportanceScores()).thenReturn(
                NewsImportanceRecalculationResponseDto.builder()
                        .totalProcessed(12)
                        .updatedCount(8)
                        .minScore(5)
                        .maxScore(92)
                        .averageScore(37.5)
                        .build()
        );

        mockMvc.perform(post("/api/v1/news/admin/recalculate-importance")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProcessed").value(12))
                .andExpect(jsonPath("$.data.updatedCount").value(8))
                .andExpect(jsonPath("$.data.minScore").value(5))
                .andExpect(jsonPath("$.data.maxScore").value(92));

        verify(newsService).recalculateImportanceScores();
    }

    @Test
    void userCannotRecalculateImportanceScores() throws Exception {
        mockMvc.perform(post("/api/v1/news/admin/recalculate-importance")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());
    }
}
