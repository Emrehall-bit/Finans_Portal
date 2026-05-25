package com.emrehalli.financeportal.ai.config;

import com.emrehalli.financeportal.ai.provider.AiProviderType;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private boolean enabled = false;
    private int timeoutSeconds = 10;
    private int maxOutputTokens = 512;

    private ProviderProperties groq = new ProviderProperties();
    private ProviderProperties gemini = new ProviderProperties();
    private RoutingProperties routing = new RoutingProperties();

    // ── Provider config ───────────────────────────────────────────

    public static class ProviderProperties {
        private boolean enabled = false;
        private String apiKey;
        private String model;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    // ── Routing config ────────────────────────────────────────────

    public static class RouteConfig {
        private AiProviderType primary = AiProviderType.GROQ;
        private AiProviderType fallback = AiProviderType.GEMINI;

        public RouteConfig() {}

        public RouteConfig(AiProviderType primary, AiProviderType fallback) {
            this.primary = primary;
            this.fallback = fallback;
        }

        public AiProviderType getPrimary() { return primary; }
        public void setPrimary(AiProviderType primary) { this.primary = primary; }
        public AiProviderType getFallback() { return fallback; }
        public void setFallback(AiProviderType fallback) { this.fallback = fallback; }
    }

    public static class RoutingProperties {
        private RouteConfig chat                 = new RouteConfig(AiProviderType.GEMINI, AiProviderType.GROQ);
        private RouteConfig pageAnalysis         = new RouteConfig(AiProviderType.GROQ,   AiProviderType.GEMINI);
        private RouteConfig technicalAnalysis    = new RouteConfig(AiProviderType.GROQ,   AiProviderType.GEMINI);
        private RouteConfig fundamentalAnalysis  = new RouteConfig(AiProviderType.GROQ,   AiProviderType.GEMINI);
        private RouteConfig companyComparison    = new RouteConfig(AiProviderType.GROQ,   AiProviderType.GEMINI);
        private RouteConfig portfolioAnalysis    = new RouteConfig(AiProviderType.GROQ,   AiProviderType.GEMINI);
        private RouteConfig newsSummary          = new RouteConfig(AiProviderType.GROQ,   AiProviderType.GEMINI);
        private RouteConfig newsImpactAnalysis   = new RouteConfig(AiProviderType.GROQ,   AiProviderType.GEMINI);

        public RouteConfig getForTask(AiTaskType taskType) {
            return switch (taskType) {
                case CHAT                 -> chat;
                case PAGE_ANALYSIS        -> pageAnalysis;
                case TECHNICAL_ANALYSIS   -> technicalAnalysis;
                case FUNDAMENTAL_ANALYSIS -> fundamentalAnalysis;
                case COMPANY_COMPARISON   -> companyComparison;
                case PORTFOLIO_ANALYSIS   -> portfolioAnalysis;
                case NEWS_SUMMARY         -> newsSummary;
                case NEWS_IMPACT_ANALYSIS -> newsImpactAnalysis;
            };
        }

        public RouteConfig getChat() { return chat; }
        public void setChat(RouteConfig chat) { this.chat = chat; }
        public RouteConfig getPageAnalysis() { return pageAnalysis; }
        public void setPageAnalysis(RouteConfig pageAnalysis) { this.pageAnalysis = pageAnalysis; }
        public RouteConfig getTechnicalAnalysis() { return technicalAnalysis; }
        public void setTechnicalAnalysis(RouteConfig technicalAnalysis) { this.technicalAnalysis = technicalAnalysis; }
        public RouteConfig getFundamentalAnalysis() { return fundamentalAnalysis; }
        public void setFundamentalAnalysis(RouteConfig fundamentalAnalysis) { this.fundamentalAnalysis = fundamentalAnalysis; }
        public RouteConfig getCompanyComparison() { return companyComparison; }
        public void setCompanyComparison(RouteConfig companyComparison) { this.companyComparison = companyComparison; }
        public RouteConfig getPortfolioAnalysis() { return portfolioAnalysis; }
        public void setPortfolioAnalysis(RouteConfig portfolioAnalysis) { this.portfolioAnalysis = portfolioAnalysis; }
        public RouteConfig getNewsSummary() { return newsSummary; }
        public void setNewsSummary(RouteConfig newsSummary) { this.newsSummary = newsSummary; }
        public RouteConfig getNewsImpactAnalysis() { return newsImpactAnalysis; }
        public void setNewsImpactAnalysis(RouteConfig newsImpactAnalysis) { this.newsImpactAnalysis = newsImpactAnalysis; }
    }

    // ── Top-level getters/setters ─────────────────────────────────

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    public ProviderProperties getGroq() { return groq; }
    public void setGroq(ProviderProperties groq) { this.groq = groq; }
    public ProviderProperties getGemini() { return gemini; }
    public void setGemini(ProviderProperties gemini) { this.gemini = gemini; }
    public RoutingProperties getRouting() { return routing; }
    public void setRouting(RoutingProperties routing) { this.routing = routing; }
}



