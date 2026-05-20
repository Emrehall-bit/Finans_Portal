package com.emrehalli.financeportal.news.provider.kap;

import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "news.providers.kap")
public class KapNewsProperties {

    private static final Logger logger = LogManager.getLogger(KapNewsProperties.class);

    private boolean enabled = true;
    private boolean schedulerEnabled = false;
    private String schedulerCron = "0 0 */2 * * *";
    private String baseUrl = "https://www.kap.org.tr";
    private String defaultCategory = "DISCLOSURE";
    private String defaultLanguage = "tr";
    private String defaultRegionScope = "TR";
    private int maxResults = 50;
    private int lookbackDays = 7;
    private int startupLookbackDays = 30;
    private int maxRangeDays = 90;
    private boolean fetchDetailEnabled = false;
    private List<String> disclosureTypes = List.of("ODA", "FR");
    private List<String> memberTypes = List.of("IGS");

    @PostConstruct
    void validate() {
        if (lookbackDays > maxRangeDays) {
            logger.warn("news.providers.kap.lookback-days ({}) exceeds max-range-days ({}). Clamping to {}.",
                    lookbackDays, maxRangeDays, maxRangeDays);
            lookbackDays = maxRangeDays;
        }
        if (startupLookbackDays > maxRangeDays) {
            logger.warn("news.providers.kap.startup-lookback-days ({}) exceeds max-range-days ({}). Clamping to {}.",
                    startupLookbackDays, maxRangeDays, maxRangeDays);
            startupLookbackDays = maxRangeDays;
        }
        if (maxResults < 1) {
            maxResults = 50;
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isSchedulerEnabled() { return schedulerEnabled; }
    public void setSchedulerEnabled(boolean schedulerEnabled) { this.schedulerEnabled = schedulerEnabled; }

    public String getSchedulerCron() { return schedulerCron; }
    public void setSchedulerCron(String schedulerCron) { this.schedulerCron = schedulerCron; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getDefaultCategory() { return defaultCategory; }
    public void setDefaultCategory(String defaultCategory) { this.defaultCategory = defaultCategory; }

    public String getDefaultLanguage() { return defaultLanguage; }
    public void setDefaultLanguage(String defaultLanguage) { this.defaultLanguage = defaultLanguage; }

    public String getDefaultRegionScope() { return defaultRegionScope; }
    public void setDefaultRegionScope(String defaultRegionScope) { this.defaultRegionScope = defaultRegionScope; }

    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }

    public int getLookbackDays() { return lookbackDays; }
    public void setLookbackDays(int lookbackDays) { this.lookbackDays = lookbackDays; }

    public int getStartupLookbackDays() { return startupLookbackDays; }
    public void setStartupLookbackDays(int startupLookbackDays) { this.startupLookbackDays = startupLookbackDays; }

    public int getMaxRangeDays() { return maxRangeDays; }
    public void setMaxRangeDays(int maxRangeDays) { this.maxRangeDays = maxRangeDays; }

    public boolean isFetchDetailEnabled() { return fetchDetailEnabled; }
    public void setFetchDetailEnabled(boolean fetchDetailEnabled) { this.fetchDetailEnabled = fetchDetailEnabled; }

    public List<String> getDisclosureTypes() { return disclosureTypes; }
    public void setDisclosureTypes(List<String> disclosureTypes) { this.disclosureTypes = disclosureTypes; }

    public List<String> getMemberTypes() { return memberTypes; }
    public void setMemberTypes(List<String> memberTypes) { this.memberTypes = memberTypes; }

    public String normalizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) return "https://www.kap.org.tr";
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
