package com.emrehalli.financeportal.ai.controller;

import com.emrehalli.financeportal.ai.access.AiFeatureAccessService;
import com.emrehalli.financeportal.ai.comparison.ComparisonAnalysisResponse;
import com.emrehalli.financeportal.ai.comparison.ComparisonAnalysisResponse.DataQuality;
import com.emrehalli.financeportal.ai.comparison.ComparisonAnalysisService;
import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.logging.AuditActivityFilter;
import com.emrehalli.financeportal.config.security.AiPremiumAccessDeniedHandler;
import com.emrehalli.financeportal.config.security.KeycloakJwtAuthenticationConverter;
import com.emrehalli.financeportal.config.security.ModerationEnforcementFilter;
import com.emrehalli.financeportal.config.security.ResourceAccessManager;
import com.emrehalli.financeportal.config.security.SecurityConfig;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AiComparisonAnalysisController.class)
@Import(SecurityConfig.class)
class AiComparisonAnalysisSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ComparisonAnalysisService comparisonAnalysisService;
    @MockBean
    private AiFeatureAccessService aiFeatureAccessService;
    @MockBean
    private KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;
    @MockBean
    private ResourceAccessManager resourceAccessManager;
    @MockBean
    private ModerationEnforcementFilter moderationEnforcementFilter;
    @MockBean
    private AiPremiumAccessDeniedHandler aiPremiumAccessDeniedHandler;
    @MockBean
    private AuditActivityFilter auditActivityFilter;
    @MockBean
    private JwtDecoder jwtDecoder;
    @MockBean
    private AppMessageSource appMessageSource;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            ((FilterChain) args[2]).doFilter((jakarta.servlet.ServletRequest) args[0], (jakarta.servlet.ServletResponse) args[1]);
            return null;
        }).when(moderationEnforcementFilter).doFilter(any(), any(), any());

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            ((FilterChain) args[2]).doFilter((jakarta.servlet.ServletRequest) args[0], (jakarta.servlet.ServletResponse) args[1]);
            return null;
        }).when(auditActivityFilter).doFilter(any(), any(), any());

        doAnswer(invocation -> {
            var response = (jakarta.servlet.http.HttpServletResponse) invocation.getArgument(1);
            response.sendError(403);
            return null;
        }).when(aiPremiumAccessDeniedHandler).handle(any(), any(), any());

        when(comparisonAnalysisService.getComparisonAnalysis("THYAO", "PGSUS"))
                .thenReturn(new ComparisonAnalysisResponse(
                        "THYAO", "PGSUS", "summary", "technical", "fundamental", "risk",
                        List.of(), List.of(), List.of(), List.of(), "final", DataQuality.PARTIAL, "groq", false
                ));
    }

    @Test
    void premiumUser_canAccessComparisonAnalysis() throws Exception {
        mockMvc.perform(get("/api/v1/ai/compare-analysis")
                        .param("left", "THYAO")
                        .param("right", "PGSUS")
                        .with(user("premium").roles("USER_PREMIUM")))
                .andExpect(status().isOk());
    }

    @Test
    void normalUser_getsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/ai/compare-analysis")
                        .param("left", "THYAO")
                        .param("right", "PGSUS")
                        .with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void guest_getsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ai/compare-analysis")
                        .param("left", "THYAO")
                        .param("right", "PGSUS"))
                .andExpect(status().isUnauthorized());
    }
}
