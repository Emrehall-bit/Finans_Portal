package com.emrehalli.financeportal.ai.controller;

import com.emrehalli.financeportal.ai.access.AiFeatureAccessService;
import com.emrehalli.financeportal.ai.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.portfolio.PortfolioAnalysisResponse;
import com.emrehalli.financeportal.ai.portfolio.PortfolioAnalysisResponse.DataQuality;
import com.emrehalli.financeportal.ai.portfolio.PortfolioAnalysisService;
import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.logging.AuditActivityFilter;
import com.emrehalli.financeportal.config.security.AiPremiumAccessDeniedHandler;
import com.emrehalli.financeportal.config.security.KeycloakJwtAuthenticationConverter;
import com.emrehalli.financeportal.config.security.ModerationEnforcementFilter;
import com.emrehalli.financeportal.config.security.ResourceAccessManager;
import com.emrehalli.financeportal.config.security.SecurityConfig;
import com.emrehalli.financeportal.portfolio.repository.PortfolioRepository;
import com.emrehalli.financeportal.user.repository.UserRepository;
import com.emrehalli.financeportal.watchlist.repository.WatchlistRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AiPortfolioAnalysisController.class)
@Import({SecurityConfig.class, ResourceAccessManager.class})
class AiPortfolioAnalysisSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortfolioAnalysisService portfolioAnalysisService;
    @MockBean
    private AiFeatureAccessService aiFeatureAccessService;
    @MockBean
    private KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;
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
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private PortfolioRepository portfolioRepository;
    @MockBean
    private WatchlistRepository watchlistRepository;

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

        when(portfolioAnalysisService.getPortfolioAnalysis(15L))
                .thenReturn(new PortfolioAnalysisResponse(
                        15L, "Premium Portfoy", null, null, null,
                        "summary", "allocation", "risk", "diversification",
                        List.of(), List.of(), List.of(), List.of(), "final",
                        DataQuality.PARTIAL, null, false,
                        AiResponseMetadata.deterministic(DataQuality.PARTIAL.name())
                ));
    }

    @Test
    void premiumOwner_canAccessOwnPortfolioAnalysis() throws Exception {
        when(portfolioRepository.existsByIdAndUserKeycloakId(15L, "owner-123")).thenReturn(true);

        mockMvc.perform(get("/api/v1/ai/portfolio-analysis/15")
                        .with(jwt("owner-123", "ROLE_USER_PREMIUM")))
                .andExpect(status().isOk());
    }

    @Test
    void normalUser_getsForbidden() throws Exception {
        when(portfolioRepository.existsByIdAndUserKeycloakId(15L, "owner-123")).thenReturn(true);

        mockMvc.perform(get("/api/v1/ai/portfolio-analysis/15")
                        .with(jwt("owner-123", "ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void guest_getsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ai/portfolio-analysis/15"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void premiumUser_cannotAccessAnotherUsersPortfolio() throws Exception {
        when(portfolioRepository.existsByIdAndUserKeycloakId(15L, "other-user")).thenReturn(false);

        mockMvc.perform(get("/api/v1/ai/portfolio-analysis/15")
                        .with(jwt("other-user", "ROLE_USER_PREMIUM")))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_canAccessAnyPortfolio() throws Exception {
        mockMvc.perform(get("/api/v1/ai/portfolio-analysis/15")
                        .with(jwt("admin-1", "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt(String subject, String role) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.subject(subject))
                .authorities(new SimpleGrantedAuthority(role));
    }
}
